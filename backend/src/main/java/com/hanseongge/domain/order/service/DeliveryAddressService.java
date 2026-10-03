package com.hanseongge.domain.order.service;

import com.hanseongge.domain.order.dto.DeliveryAddressRequest;
import com.hanseongge.domain.order.dto.DeliveryAddressResponse;
import com.hanseongge.domain.order.entity.DeliveryAddress;
import com.hanseongge.domain.order.repository.DeliveryAddressRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배송지 주소록.
 *
 * <p>주문은 주소를 자기 칸에 복사해 가므로, 여기를 고치거나 지워도 지나간 주문은 그대로다.
 */
@Service
@RequiredArgsConstructor
public class DeliveryAddressService {

    /**
     * 회원당 최대 개수.
     *
     * <p>상한을 두지 않으면 주문할 때마다 "다음에도 사용" 을 눌러 수백 줄이 쌓인다.
     * 그러면 고르는 화면이 쓸모없어지고, 쓰지 않는 개인정보만 남는다.
     */
    public static final int MAX_PER_USER = 10;

    private final DeliveryAddressRepository repository;

    @Transactional(readOnly = true)
    public List<DeliveryAddressResponse> list(Long userId) {
        return repository.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(userId).stream()
                .map(DeliveryAddressResponse::from)
                .toList();
    }

    @Transactional
    public DeliveryAddressResponse create(Long userId, DeliveryAddressRequest request) {
        long count = repository.countByUserId(userId);
        if (count >= MAX_PER_USER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "배송지는 최대 " + MAX_PER_USER + "개까지 저장할 수 있습니다. 쓰지 않는 배송지를 지워 주세요.");
        }

        DeliveryAddress address = DeliveryAddress.of(userId, request.label(), request.receiverName(),
                request.receiverPhone(), request.zipcode(), request.address1(), request.address2());

        // 첫 배송지는 요청과 무관하게 기본이 된다. 기본이 하나도 없으면 주문서가
        // 아무것도 고르지 못해, 저장해 둔 보람이 없다.
        boolean first = count == 0;
        if (first || request.setDefault()) {
            repository.clearDefault(userId);
            address.markDefault(true);
        }
        return DeliveryAddressResponse.from(repository.save(address));
    }

    @Transactional
    public DeliveryAddressResponse update(Long userId, Long addressId, DeliveryAddressRequest request) {
        DeliveryAddress address = mine(userId, addressId);
        address.apply(request.label(), request.receiverName(), request.receiverPhone(),
                request.zipcode(), request.address1(), request.address2());
        if (request.setDefault() && !address.isDefault()) {
            repository.clearDefault(userId);
            address.markDefault(true);
        }
        return DeliveryAddressResponse.from(address);
    }

    @Transactional
    public void makeDefault(Long userId, Long addressId) {
        DeliveryAddress address = mine(userId, addressId);
        if (address.isDefault()) {
            return;
        }
        repository.clearDefault(userId);
        address.markDefault(true);
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        DeliveryAddress address = mine(userId, addressId);
        boolean wasDefault = address.isDefault();
        repository.delete(address);

        // 기본을 지웠으면 남은 것 중 맨 위를 기본으로 올린다. 비워 두면 다음 주문에서
        // 주소가 하나도 안 골라진 채 열려, 저장해 둔 의미가 없다.
        if (wasDefault) {
            repository.flush();
            repository.findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(userId).stream()
                    .findFirst()
                    .ifPresent(next -> next.markDefault(true));
        }
    }

    /**
     * 내 배송지인가.
     *
     * <p>남의 것이면 403 이 아니라 404 를 준다 — 403 은 "그 번호의 배송지가 있긴 하다"를
     * 알려 주어, 번호를 훑으면 남이 주소를 몇 개 저장했는지 셀 수 있다.
     */
    private DeliveryAddress mine(Long userId, Long addressId) {
        return repository.findById(addressId)
                .filter(address -> address.ownedBy(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배송지를 찾을 수 없습니다."));
    }
}
