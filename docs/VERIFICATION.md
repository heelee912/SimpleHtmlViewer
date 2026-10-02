# 요구사항 검증 기록

대상은 릴리스 `v1.1-debug`의 `SimpleHtmlViewer-redesign-debug.apk`입니다. 앱 버전은 1.1 / versionCode 2이며 APK SHA256은 `83742AC8FB566C84E9EF93164114CED3D545EBC6B3FEB013660219EE07BCF104`입니다. 요구사항 표는 [README](../README.md#요구사항과-달성-상태)에 있습니다.

## 실행한 검사

| 범위 | 결과 | 원문 로그 |
|---|---:|---|
| 실제 SAF 선택·인라인 JS/CSS·기본 전체화면·도구·접근성 동작·권한/localStorage·회전·외부 링크 분기·권한 철회 | 21 통과 / 0 실패 | [suite.log](verification/suite.log) |
| 프로세스 강제 종료 후 파일 권한·localStorage·읽기 화면 복구 | 2 통과 / 0 실패 | [relaunch.log](verification/relaunch.log) |
| 파일 삭제 후 복구 UI | 1 통과 / 0 실패 | [deleted.log](verification/deleted.log) |
| 320dp 폭·200% 글씨·가로 첫 화면·적응형/단색 아이콘 | 3 통과 / 0 실패 | [presentation.log](verification/presentation.log) |
| 같은 큰 글씨 조건의 파일 복구 화면 | 1 통과 / 0 실패 | [presentation-deleted.log](verification/presentation-deleted.log) |
| 최종 APK에서 실제 Google Maps 앱으로 전환하고 기존 문서로 복귀 | 1 통과 / 0 실패 | [maps-installed.log](verification/maps-installed.log) |
| Maps 사용 불가 시 외부 브라우저/시스템으로 전달·복귀와 geo 처리 앱 없음 | 2 통과 / 0 실패 | [maps-unavailable.log](verification/maps-unavailable.log) |

합계 **Android 31 검사**입니다. 모두 API35의 전용 `SimpleHtmlViewerApi35` 에뮬레이터에서 합성 HTML을 사용했습니다. 마지막 Maps 3 검사는 2026-10-03 KST에 위 해시의 배포 APK를 설치해 실행했습니다. 앱 코드를 바꾸거나 새 APK로 대체하지 않았습니다.

Maps 설치 검사에서는 Intent 요청만 가로채는 방식이 아니라 접근성 창의 실제 패키지가 `com.google.android.apps.maps`가 됐는지 확인했습니다. 돌아온 뒤 문서 URL과 localStorage의 방문 횟수가 동일한지 확인해 문서가 유지됐음을 검사했습니다. Maps 사용 불가는 전용 에뮬레이터의 Maps 앱만 잠시 비활성화해 재현했고 검사 후 다시 활성화했습니다. 다른 에뮬레이터나 실제 휴대폰은 조작하지 않았습니다.

별도의 JDK 정책 검사 `:app:policyTest`는 57 assertions를 통과했습니다. 문서별 origin·외부 링크 허용 규칙·제목·CSS 색상 해석을 확인합니다. 배포 APK 검토 당시 lint는 오류 0 / 경고 4였습니다. 공개용 소스에서도 빌드 및 같은 57 assertions가 통과했고 lint 오류 0 / 경고 3을 확인했습니다. 공개 소스로 빌드한 APK와 배포 APK는 ZIP 내부 파일 구성과 각 파일의 압축 해제 바이트가 모두 일치했습니다.

## 코드 근거

- [DocumentSource](../app/src/main/java/com/triphtml/viewer/DocumentSource.java): `takePersistableUriPermission`으로 선택 파일의 지속 읽기 권한을 받고 매번 원본 접근을 확인합니다.
- [DocumentAddress](../app/src/main/java/com/triphtml/viewer/DocumentAddress.java): 선택 URI로 문서별 고정 origin을 만듭니다. `.invalid` 주소는 파일을 WebView에 제공하기 위한 내부 주소이며 서버를 실행하지 않습니다.
- [ViewerActivity](../app/src/main/java/com/triphtml/viewer/ViewerActivity.java): JS·DOM storage, 기본 렌더링, 전체화면, 도구 열기/숨기기, 사용자 외부 탐색과 새 창을 처리합니다.
- [ExternalLinks](../app/src/main/java/com/triphtml/viewer/ExternalLinks.java): 안전한 `ACTION_VIEW`를 만들고 Google Maps 패키지를 먼저 시도합니다. 외부 화면을 열어도 문서 WebView는 유지됩니다.
- [LinkPolicy](../app/src/main/java/com/triphtml/viewer/LinkPolicy.java): 내부 문서·외부 링크·차단을 구분합니다. 사용자 조작 없는 외부 실행은 막습니다.
- [AndroidManifest](../app/src/main/AndroidManifest.xml)와 [빌드 설정](../app/build.gradle): 요청 권한은 인터넷 하나이며 광고 SDK·서버·런타임 외부 라이브러리 의존성이 없습니다.
- [Android 검사 구현](../app/src/androidTest/java/com/triphtml/viewer/ViewerInstrumentation.java): 각 검사에 사용한 조건과 판정을 공개합니다.

## 지원 범위와 미검증

- 단일 HTML의 인라인 JavaScript·CSS와 문서별 localStorage를 지원합니다. 별도 상대경로 CSS·JS·이미지는 읽을 권한이 없어 지원하지 않습니다. HTTPS 자원은 네트워크가 필요합니다.
- 일반 링크·새 탭 링크·클릭 직후 `window.open`을 지원합니다. 지연된 팝업·빈 창을 열고 나중에 주소를 넣는 흐름·알 수 없는 scheme·iframe·object·form 제출·worker는 지원하지 않습니다.
- 파일 삭제·이동·제공자의 권한 철회·앱 데이터 삭제까지 저장을 보장하지 않습니다. 이런 경우에는 다시 선택하거나 문서 데이터를 새로 저장해야 합니다.
- API35 결과를 모든 제조사·Android 버전으로 일반화하지 않습니다. 이번 최종 APK의 실제 휴대폰 표시와 API26–32 실행은 별도 검증하지 않았습니다.
- 접근성 트리·초점·동작과 단색 아이콘 레이어를 확인했지만 TalkBack 음성 탐색 전체와 실제 런처의 테마 아이콘 활성 상태는 확인하지 않았습니다.
- 기능 검사 통과는 화면 디자인의 만족도를 보증하지 않습니다. UI/UX 평가는 별도로 다룹니다.
