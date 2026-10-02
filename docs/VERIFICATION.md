# 요구사항 검증 기록

## 2.0.1 배포 검증

공개 1.1/code 2와 비교해 Kotlin/Compose 첫 화면·문서 도구·아이콘과 HTML dialog 뒤로 가기 처리가 실제로 달라졌습니다. 로컬 2.0/code 3 검토본도 업데이트할 수 있도록 **2.0.1/code 4**로 올렸습니다. 프레임워크를 다시 교체하거나 HTML만 바꾼 동일 APK를 재발행한 것이 아닙니다.

| 파일 | 크기 | SHA256 |
|---|---:|---|
| SimpleHtmlViewer-2.0.1-debug.apk | 9,443,710 bytes | `36548BC4A7D984BF5B98DCBEB655D396A539733ABE257C22612882FF7BF04E0F` |
| travel-itinerary-template.html | 707,347 bytes | `D64C6F0794A51978AF5CC1337E8864AB27EF6ECDA3772EC0D6324F4A6A40A376` |

전용 API35 AVD `SimpleHtmlViewerApi35` / `emulator-5580`에서 검사했습니다. WebView 124 계열을 사용했습니다. 검사 후 설치된 base.apk를 꺼내 위 배포 APK와 SHA256 일치를 확인했습니다. 실폰·Yomi AVD·브라우저 프로필은 조작하지 않았습니다. AVD의 글씨 크기 1.0·기본 해상도·Maps 활성 상태를 복원하고 홈으로 반환했습니다.

| 범위 | 결과 | 원문 로그 |
|---|---|---|
| 시간표 도메인·본문 연결·작성 안내 누출·매진 막차 회귀 | 70 통과 / 0 실패 | [rules.log](verification/v2.0.1/rules.log) |
| Kotlin 문서 origin·제목·링크·색상 | 18 통과 / 실패·오류 0 | [unit-tests.txt](verification/v2.0.1/unit-tests.txt) |
| lint / 앱·시험 APK 빌드 | 오류 0·경고 12 / 성공 | [build-summary.txt](verification/v2.0.1/build-summary.txt) |
| 최신 템플릿 모달·전후편·안전편·화면 누출·5/8일·상태 격리·Maps·회전 | 1 JUnit 통과 | [final-itinerary.log](verification/v2.0.1/final-itinerary.log) |
| 템플릿 문서의 프로세스 종료 후 복원 | 1 JUnit 통과 | [final-itinerary-relaunch.log](verification/v2.0.1/final-itinerary-relaunch.log) |
| 기본 뷰어 파일 선택·저장·앵커·외부 링크·권한 철회·재생성 | 1 JUnit 통과 | [final-reader.log](verification/v2.0.1/final-reader.log) |
| 기본 문서의 프로세스 종료 후 복원 | 1 JUnit 통과 | [final-reader-relaunch.log](verification/v2.0.1/final-reader-relaunch.log) |
| Maps 설치 상태의 실제 전환·복귀 | 1 JUnit 통과 | [final-reader-maps.log](verification/v2.0.1/final-reader-maps.log) |
| 파일 삭제 시 복구 | 1 JUnit 통과 | [deleted.log](verification/v2.0.1/deleted.log) |
| Maps 사용 불가 시 브라우저/시스템 전달과 처리 앱 없음 | 1 JUnit 통과 | [maps-unavailable.log](verification/v2.0.1/maps-unavailable.log) |
| 200% 글씨·320dp 폭·가로/세로 도구·첫 화면·컬러/단색 아이콘 | 2 JUnit 통과 | [presentation.log](verification/v2.0.1/presentation.log) |
| 200% 가로 화면의 파일 복구 | 1 JUnit 통과 | [presentation-deleted.log](verification/v2.0.1/presentation-deleted.log) |

현재 APK의 Android 검사 합계는 **10 JUnit tests**입니다. 각 여정 안에 여러 순차 검사가 있습니다. 매진 막차 회귀는 최종 HTML로 재실행했으며 18:00 노선 막차·11:40 최후 안전편·다른 안전편의 유지와 잘못된 미운행 오류가 없음을 실제 WebView에서 확인했습니다.

### 1.1에서 실제 업데이트

GitHub에서 실제 공개 APK `83742AC8…BCF104`를 다운로드했습니다. 전용 AVD에 설치하고 파일을 선택해 `persist-proof=durable-value`를 저장했습니다. 구버전의 별도 프로세스 재실행으로 지속 URI 권한·localStorage·기본 읽기 화면을 확인한 뒤 앱 삭제나 데이터 초기화 없이 `adb install -r`로 위 2.0.1 APK를 설치했습니다. 새 앱의 `RelaunchTest`에서 같은 값·지속 읽기 권한·기본 전체화면을 확인했습니다.

- [구버전 재실행 2개 통과](verification/v2.0.1/upgrade-legacy-relaunch.log)
- [업데이트 후 재실행 1 JUnit 통과](verification/v2.0.1/upgrade-2.0.1-relaunch.log)
- [서명 원문](verification/v2.0.1/apk-signature.txt): 공개 1.1·로컬 2.0·이번 2.0.1 모두 인증서 SHA256 `f03b8db04c27daddb04b32f60c49e637a9537ddcac36609d882fd8b03e166467`.

업데이트 준비용 [구버전 전체 검사](verification/v2.0.1/upgrade-legacy-suite.log)는 18 통과·3 실패였습니다. 회전 직후 앵커 터치가 다른 창을 대상으로 거부됐고 후속 앵커/이전 위치 검사도 실패했습니다. 이를 성공으로 합산하지 않았으며 새 2.0.1 전체 검사와 별도로 기록합니다. 데이터 유지의 직접 근거는 위 구버전/업데이트 후 재실행 검사입니다.

Android의 업데이트 조건은 [버전 코드](https://developer.android.com/studio/publish/versioning)와 [앱 서명](https://developer.android.com/studio/publish/app-signing) 문서를 기준으로 확인했습니다. 이 배포는 기존과 같은 Debug 인증서입니다. 다른 컴퓨터에서 새로 생성한 Debug 키로 빌드하면 기존 배포본을 덮어쓸 수 없습니다.

### 권한·공개 범위·한계

플랫폼 권한은 기존과 같은 INTERNET입니다. AndroidX의 `com.triphtml.viewer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 서명 권한이 추가됐으며 새 런타임 승인·전체 저장소·사진·연락처·위치 권한은 없습니다. APK ZIP에 HTML·시험 자산·서명 키가 없음을 확인했습니다. 공개 소스는 허용한 앱·시험·빌드·템플릿 파일만 포함합니다. 개인 경로·Library 메타데이터·키 형태의 문자열을 검사했고 기본 여행 JSON이 기존 빈 템플릿과 같은지 대조했습니다. 작성용 가상 예시는 비렌더링 영역에만 있습니다.

실폰과 API26–32 실행, TalkBack 실제 음성 탐색, 런처 테마 아이콘의 사용자 설정 화면은 이번에 확인하지 않았습니다. lint 경고 12개는 SDK/의존성의 새 버전 안내·API33 전용 manifest 속성·KTX 제안입니다. 새 의존성으로 갈아타는 작업은 포함하지 않았습니다. 시간표의 공식 출처 진실성이나 입력하지 않은 후속 조건을 자동 보증하지 않습니다. 최후 안전편은 현재 선택한 날의 등록된 모든 후속 일정에 한정됩니다.

GitHub Actions는 [.github/workflows/android.yml](../.github/workflows/android.yml)에 정의한 시간표 70개·lint·Kotlin 18개·앱/시험 APK 빌드를 정확한 커밋에서 실행합니다. Android UI 검사는 위 로컬 전용 AVD에서 실행한 결과입니다. CI에서 생성한 별도 서명의 APK는 릴리스에 사용하지 않습니다.

## 1.1 이전 배포 기록

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
