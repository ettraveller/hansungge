import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // deploy.sh 가 standalone 산출물을 모아 두는 곳. 우리가 쓴 코드가 아니라
    // Next 가 생성한 번들이라, 검사하면 고칠 수 없는 오류가 수천 개 나온다 —
    // 그러면 lint 결과 전체를 아무도 안 보게 된다.
    ".deploy/**",
  ]),
]);

export default eslintConfig;
