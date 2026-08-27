package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderBySortAscIdAsc();

    List<Category> findByParentIdAndActiveTrueOrderBySortAscIdAsc(Long parentId);
}
