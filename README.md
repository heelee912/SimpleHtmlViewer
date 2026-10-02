# 여행 HTML 뷰어

여행 일정 HTML을 전체화면으로 읽는 Android 앱입니다. Kotlin·Jetpack Compose로 첫 화면과 문서 도구를 만들고 Android WebView로 HTML을 표시합니다. 광고·계정·백엔드·로컬 HTTP 서버가 없습니다.

## 요구사항과 달성 상태

아래는 이 앱의 필수 요구사항입니다. **모두 구현됐으며 단일 HTML 지원 범위에서 API35 에뮬레이터로 해당 동작을 확인했습니다.** 광고·서버·소스 편집 기능의 부재는 소스와 빌드 구성을 확인했습니다. 모든 Android 기기와 모든 HTML 기능의 호환성을 검증했다는 뜻은 아닙니다. 배포 APK의 검사 근거와 한계는 [검증 기록](docs/VERIFICATION.md)에 있습니다.

| 필수 요구사항 | 현재 동작과 확인 |
|---|---|
| 앱 안에서 HTML 파일을 직접 선택하고 다시 열 수 있음 | Android 파일 선택창으로 열고 다른 파일로 바꿀 수 있습니다. 마지막 문서는 앱 재실행 시 다시 엽니다. 실제 파일 선택·취소·재열기 검사를 통과했습니다. |
| 새로고침해도 파일 접근권한 유지 | 선택 파일의 SAF 지속 읽기 권한을 저장합니다. 새로고침·프로세스 재실행·Activity 재생성·회전 후 접근 유지 검사를 통과했습니다. |
| JS / CSS / DOM storage 정상 동작 | JavaScript와 DOM storage를 켜고 문서마다 고정된 origin을 사용합니다. 인라인 JS·CSS와 문서별 localStorage 유지·격리를 확인했습니다. |
| HTML 소스 편집 화면이 아니라 렌더링 화면이 기본 | 기본 화면은 WebView가 렌더링한 문서입니다. 소스 편집 기능은 없습니다. |
| 광고 없음 | 광고 UI·광고 SDK·분석 SDK가 없습니다. |
| 서버 없음 | 앱 백엔드나 로컬 HTTP 서버를 실행하지 않습니다. 선택 파일을 WebView에 직접 제공합니다. HTML의 외부 HTTPS 자원은 인터넷을 사용할 수 있습니다. |
| 이상한 상시 설정 버튼·팝업 없음 | 읽는 중 상시 설정 버튼과 반복 안내 팝업이 없습니다. 사용자가 뒤로 가기를 누를 때만 문서 도구를 표시합니다. 필요한 오류는 닫을 수 있는 안내로 표시합니다. |
| 화면을 거의 전체화면으로 볼 수 있음 | 문서가 기본 전체화면을 사용하며 시스템 막대는 숨깁니다. 카메라 컷아웃은 피합니다. 문서 영역 검사로 확인했습니다. |
| 필요하면 주소창/툴바를 숨길 수 있음 | 주소창은 없습니다. 도구는 기본 숨김이며 **뒤로 가기 → 문서 도구**, **읽기 계속 또는 바깥 영역 누르기 → 숨김**으로 사용합니다. 문서 크기·스크롤 유지도 확인했습니다. |
| **HTML 외부 링크를 WebView 안에서 소비하지 않고 Android에 전달** | 사용자 링크를 `ACTION_VIEW` Intent로 보냅니다. 일반 링크·`target="_blank"`·사용자 클릭의 즉시 `window.open`을 확인했습니다. 내부 앵커는 현재 문서에 남습니다. |
| **Google Maps 링크를 곧바로 Maps 앱으로 전달** | 설치된 `com.google.android.apps.maps`를 먼저 지정해 실행합니다. 최종 APK로 실제 Maps 앱 전환과 기존 문서 복귀를 확인했습니다. Maps 사용 불가 시 브라우저/시스템 전달과 처리 앱 없음도 확인했습니다. |

파일 삭제·이동·제공자의 권한 철회는 앱이 막을 수 없으므로 다시 선택해야 합니다. 별도 상대경로 CSS·JS·이미지는 지원하지 않습니다. Android 파일 선택창과 시스템 자체 안내 및 HTML 내부 UI는 앱의 반복 안내 팝업과 별개입니다.

## 다운로드

[릴리스에서 APK와 여행 일정 템플릿 다운로드](https://github.com/heelee912/SimpleHtmlViewer/releases/tag/v2.0.1-debug)

- 앱 이름: **여행 HTML 뷰어**
- 패키지: `com.triphtml.viewer`
- Android 8.0 이상 / 앱 버전 2.0.1 / versionCode 4
- APK: `SimpleHtmlViewer-2.0.1-debug.apk` — 9,443,710 bytes
- SHA256: `36548BC4A7D984BF5B98DCBEB655D396A539733ABE257C22612882FF7BF04E0F`
- 현재 배포물은 Android Debug 서명의 APK입니다.
- [여행 일정 템플릿](templates/여행%20일정%20템플릿.html)은 별도 HTML 파일입니다. APK에 들어 있지 않습니다.

공개 1.1/code 2와 로컬 검토본 2.0/code 3보다 높은 버전입니다. 기존 공개 APK와 같은 패키지·런처·서명 인증서를 유지했습니다. 1.1 위에 실제 업데이트 설치 후 기존 문서·지속 읽기 권한·localStorage 유지를 전용 에뮬레이터에서 확인했습니다. **앱을 삭제하거나 데이터를 지울 필요가 없습니다.**

## 사용

1. 첫 화면의 **HTML 파일 열기**로 파일을 선택합니다.
2. 문서는 기본 전체화면으로 표시합니다. **뒤로 가기**는 열린 HTML dialog를 먼저 닫고 그다음 문서 도구를 엽니다. dialog가 없다면 바로 문서 도구가 열립니다.
3. 도구에서 **새로고침**·**다른 파일 열기**·**읽기 계속**을 선택합니다. 도구가 열린 상태에서 뒤로 가기를 다시 누르면 앱을 나갑니다.
4. 다시 실행하면 마지막 문서를 엽니다. 같은 문서를 새로고침하거나 Activity가 재생성돼도 지속 파일 권한과 문서별 localStorage를 유지합니다.
5. 외부 링크는 사용자 조작으로 Android 앱에 전달합니다. Maps 링크는 설치된 Google Maps를 우선 시도합니다.

TalkBack의 문서 접근성 동작 **문서 도구 열기**로도 도구를 열 수 있습니다. 큰 글씨와 낮은 가로 화면에서는 도구와 안내를 스크롤할 수 있습니다.

## HTML 지원 범위

단일 HTML의 인라인 JavaScript·CSS·data 이미지와 HTTPS 자원을 지원합니다. 별도 상대경로 CSS·JS·이미지는 단일 파일 접근권한만으로 읽을 수 없어 지원하지 않습니다. HTTPS 자원에는 인터넷이 필요합니다. 템플릿의 Pretendard 글꼴은 외부 HTTPS CSS를 참조합니다.

내부 앵커는 문서 안에서 이동합니다. 사용자가 누르는 일반 링크·`target="_blank"`·즉시 실행하는 `window.open`을 외부 앱에 전달합니다. 지연된 팝업과 알 수 없는 URL scheme은 차단합니다. 파일 이동·삭제·제공자 권한 철회 시에는 다시 선택해야 합니다. 앱 데이터 삭제·앱 제거로 사라진 localStorage는 복구할 수 없습니다.

앱은 iframe·object·form 제출·worker·HTTP 혼합 콘텐츠를 허용하지 않습니다. JavaScript 네이티브 인터페이스나 인증서 검증 해제를 사용하지 않습니다.

## 개인정보와 권한

플랫폼 권한은 `INTERNET`입니다. AndroidX가 추가하는 앱 내부 서명 권한 `com.triphtml.viewer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`도 선언됩니다. 이는 사진·연락처·위치·저장소 권한이나 사용자에게 승인받는 런타임 권한이 아닙니다. Android 파일 선택창에서 선택한 문서의 지속 읽기 권한만 사용합니다.

배포 APK에는 사용자의 여행 문서·예약 정보·휴대폰 저장값이 들어 있지 않습니다. 선택 파일 URI와 제목 및 문서별 웹 저장값은 사용하는 휴대폰의 앱 저장공간에 남으며 다른 사용자에게 배포되는 APK에 포함되지 않습니다. Android 백업은 비활성화돼 있습니다.

앱 자체의 광고·분석·서버 업로드 코드는 없습니다. 사용자가 연 HTML에 HTTPS 글꼴·이미지 등이 있으면 WebView가 해당 서버에 요청합니다. 외부 링크를 누르면 대상 Android 앱이 열립니다.

첨부 템플릿의 기본 여행 데이터에서 연락처·예약번호·주소·날짜는 비어 있으며 장소명과 숙소명은 일반 자리표시자입니다. 작성 계약과 완성 가상 예시는 소스의 비렌더링 영역에 있습니다. 가상 예시는 기본 여행 데이터로 읽거나 화면에 표시하지 않습니다. 실제 개인 여행·예약·성명은 공개하지 않았습니다.

## 시간표 템플릿

기존 일정 카드와 시간표 버튼·모달을 유지합니다. 시간표는 예정편뿐 아니라 의미 있는 조기 출발·지연 범위와 일정 손실이 시작되는 편을 함께 다룹니다. **노선의 실제 막차**와 **현재 선택한 날의 이후 일정을 유지할 수 있는 최후 안전편**을 구분합니다. 매진인 막차도 실제 운행한다면 노선 막차로 남고 다른 유효 안전편을 지우지 않습니다.

도보·승차 여유·고정 환승·최소 관람·마지막 입장·행사·숙소 마감에서 역산하고 본문 일정의 순서·시각·조건과 대조합니다. 운행일·요일·운휴·예약·공식 출처·확인일이 빠지거나 본문과 어긋나면 안전을 확정하지 않습니다. 실제 출처의 진실성과 조사 범위를 자동으로 보증하거나 여러 날의 우회 경로를 계산하지는 않습니다.

5일·8일처럼 데이터 일수에 맞춰 날짜를 표시합니다. 확인 표시와 선택 대안은 파일·여행별로 분리 저장합니다. 상세 모달은 스크롤·닫기·뒤로 가기·회전·Maps 이동과 복귀를 지원합니다. 작성 안내와 가상 예시를 숨기는 구조는 HTML 안에 있으며 작성자의 수동 삭제에 의존하지 않습니다.

배포 템플릿은 빈 작성용 문서입니다. 완성 여행 검사에서는 의도적으로 거부됩니다. 실제 여행 파일을 작성한 뒤 `node scripts/validate-itinerary.mjs --final FILE.html`로 검사할 수 있습니다.

## 빌드

JDK 17 이상과 Android SDK 35가 필요합니다. Gradle 8.14 Wrapper와 Android Gradle Plugin 8.11.1을 사용합니다. `ANDROID_HOME`을 SDK 경로로 지정하거나 개인 `local.properties`의 `sdk.dir`에 지정하세요. 개인 설정·서명 키는 커밋하지 않습니다.

```sh
node scripts/test-itinerary.mjs
node scripts/prepare-itinerary-fixtures.mjs
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Windows에서는 `gradlew.bat`을 사용합니다. APK 출력은 `app/build/outputs/apk/debug/app-debug.apk`입니다. 다른 컴퓨터의 Debug 서명은 배포 서명과 달라 기존 설치본을 덮어쓸 수 없을 수 있습니다.

## 검사

시간표 규칙 70개와 Kotlin 단위 검사 18개를 사용합니다. API35 전용 에뮬레이터의 실제 DocumentsUI와 WebView에서 최신 템플릿과 최종 APK를 검사합니다. 테스트 제공자와 합성 여행 파일은 별도 테스트 APK에만 있습니다. [실행 로그와 확인 범위](docs/VERIFICATION.md).

`app/src/test`는 문서 origin·링크 정책·제목·CSS 색상을 검사합니다. `app/src/androidTest`는 파일 선택·저장·회전·재실행·도구·복구·큰 글씨·아이콘·시간표 모달을 검사합니다. 합성 이메일과 장소 URL은 테스트 데이터입니다. GitHub Actions는 정확한 커밋의 시간표 규칙·lint·Kotlin 단위 검사와 앱/시험 APK 빌드를 실행합니다. CI가 만든 별도 Debug 키의 APK는 배포하지 않습니다.

`scripts/verify-emulator.ps1 -Serial emulator-5580`은 이름이 `SimpleHtmlViewerApi35`인 전용 에뮬레이터만 허용합니다. Maps 비활성화·해상도·글씨 크기 변경을 잠시 적용한 뒤 복원합니다. 공유 기기에서 실행하지 마세요. `verify-itinerary.ps1`은 시간표·기본 뷰어 여정만 실행하고 `verify-presentation.ps1`은 200% 글씨·좁은 화면을 검사합니다.

현재 APK의 실폰 표시·API26–32 실기 실행·TalkBack 음성 탐색·실제 런처 테마 아이콘 활성 상태는 이번 자동 검사로 확인하지 않았습니다.

## 소스 구조

- `domain/DocumentAddress`·`LinkPolicy`·`DocumentTitle`·`PageColor`: Android I/O와 분리된 문서·링크·표현 규칙.
- `DocumentSource`: SAF 지속 권한을 확인하고 선택한 문서만 읽습니다.
- `ExternalLinks`: 사용자 링크를 안전한 Android Intent로 전달합니다.
- `ui/StartScreens`·`DocumentToolsSheet`·`ReaderApp`: Compose 첫 화면·일시 도구·뒤로 가기를 담당합니다.
- `reader/DocumentReader`: WebView 수명주기·문서 렌더링·HTML dialog를 담당합니다.
- `ViewerActivity`: WebView 수명주기·파일 선택·뒤로 가기 흐름을 연결합니다.

## 라이선스

앱 소스와 제공한 HTML 템플릿은 [MIT License](LICENSE)입니다. 외부 구성요소는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)의 원래 라이선스를 따릅니다.
