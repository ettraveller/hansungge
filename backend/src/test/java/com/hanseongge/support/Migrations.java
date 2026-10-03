package com.hanseongge.support;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code db/migration} 의 버전 번호를 읽는다.
 *
 * <p>예전에는 테스트가 {@code "20"} 처럼 숫자를 직접 들고 있었다. 그러면 마이그레이션을
 * 하나 더할 때마다 관련 없는 테스트 셋이 같이 빨개진다 — 실제로 V20 에 멈춰 있는 동안
 * V26 까지 가서, 기능과 무관한 실패가 쌓였다. 숫자를 파일에서 읽어 그 고리를 끊는다.
 *
 * <p>검증력은 오히려 올라간다. "마이그레이션 파일 전부가 실제로 적용됐는가" 를 보게 되므로,
 * 하나가 조용히 빠지면 여기서 걸린다.
 */
public final class Migrations {

    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__");

    private Migrations() {
    }

    /** 적용되어야 할 버전 전체. 번호 순이다. */
    public static List<String> versions() {
        Resource[] files;
        try {
            files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:db/migration/V*__*.sql");
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션 파일을 읽지 못했습니다", e);
        }
        if (files.length == 0) {
            throw new IllegalStateException("db/migration 에 마이그레이션이 없습니다");
        }
        return java.util.Arrays.stream(files)
                .map(Resource::getFilename)
                .map(VERSION::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
    }

    /** 가장 높은 버전. 헬스체크가 돌려주는 {@code schemaVersion} 과 같아야 한다. */
    public static String latest() {
        List<String> all = versions();
        return all.get(all.size() - 1);
    }
}
