# 위젯 오버레이 사용성 개선 계획

## 1. 개선 목표

프로토타입의 핵심 기능은 정상 동작하므로, 사용성과 탐색 효율을 개선한다. 큰 구조 변경 없이 위젯 선택 경험과 메인 화면 구성에 집중한다.

| 우선순위 | 개선 항목 | 현재 문제 | 목표 |
|---|---|---|---|
| **P0** | 커스텀 위젯 선택기 | 시스템 선택기는 앱 이름만 표시. 같은 앱의 여러 위젯 구분 불가 | 앱별 그룹핑 + 개별 위젯 이름 + 미리보기 표시 |
| **P1** | 메인 화면 UI 재구성 | 버튼 일렬 나열, 기능 그룹 구분 약함 | 카드형 섹션 + 위젯 미리보기 상단 배치 |
| **P2** | 시각적 피드백 강화 | 상태가 텍스트로만 표시 | 선택/권한/오버레이 상태를 아이콘+색상으로 표현 |

## 2. P0: 커스텀 위젯 선택기

### 2.1 현재 동작

```kotlin
// WidgetHostController.kt:26-33
fun beginWidgetPick(activity: Activity) {
    val id = host.allocateAppWidgetId()
    pendingWidgetId = id
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    activity.startActivityForResult(intent, REQUEST_PICK_WIDGET)
}
```

시스템 `ACTION_APPWIDGET_PICK` 인텐트는 앱 이름만 그룹화하여 표시한다. Google Clock이 6개 위젯을 제공하면 모두 "시계"로 보인다.

### 2.2 개선 방안

**새 파일: `WidgetPickerActivity.kt`** — 커스텀 위젯 선택 화면

```
위젯 선택기 구조:
┌─────────────────────────────┐
│ 🔍 검색                     │
├─────────────────────────────┤
│ ▼ Google 시계               │
│   ┌─────┐  시계 (4x2)      │
│   │ img │  Alarm (2x2)     │
│   └─────┘  도시 시계 (4x3)  │
│                             │
│ ▼ Samsung Weather           │
│   ┌─────┐  날씨 (4x2)      │
│   │ img │  날씨 (2x2)      │
│   └─────┘                   │
└─────────────────────────────┘
```

**핵심 API:**

```kotlin
// 모든 설치된 위젯 제공자 조회
val providers = AppWidgetManager.getInstance(context).installedProviders

// 그룹핑: provider.packageName + provider.provider.className로 앱별 분류
val grouped = providers.groupBy { it.provider.packageName }

// 개별 위젯 정보
info.loadLabel(packageManager)        // "시계", "Alarm", "도시 시계"
info.loadPreviewImage(packageManager) // 미리보기 Bitmap (없을 수 있음)
info.initialLayout                    // 대체 레이아웃 리소스
info.minWidth / info.minHeight        // 크기 (dp)
```

**구현 상세:**

1. `WidgetPickerActivity` — RecyclerView + ExpandableListView 또는 단순 그룹 리스트
   - 앱 아이콘 + 앱 이름 헤더 (접기/펼치기)
   - 각 위젯 항목: 미리보기 이미지(또는 initialLayout 렌더) + 위젯 이름 + 크기
   - 탭 시 `AppWidgetHost.allocateAppWidgetId()` → 바인딩 → 설정 Activity → 완료

2. `WidgetHostController` 수정
   - `beginWidgetPick()`를 커스텀 선택기 호출로 변경
   - 시스템 선택기 대신 `WidgetPickerActivity` 시작
   - 바인딩은 `ACTION_APPWIDGET_BIND` 또는 `bindAppWidgetIdIfAllowed()` 사용

3. 매니페스트에 `WidgetPickerActivity` 등록

**주의사항:**
- `bindAppWidgetIdIfAllowed()`는 `BIND_APPWIDGET` 보호 권한이 필요. 일반 앱은 시스템 승인 흐름(`ACTION_APPWIDGET_BIND`)을 거쳐야 함
- `loadPreviewImage()`는 null을 반환하는 제공자가 많으므로 `initialLayout`으로 대체 렌더링 필요
- 커스텀 선택기에서 직접 바인딩이 거부되면 시스템 선택기로 폴백

### 2.3 바인딩 전략

```
사용자가 위젯 탭
    │
    ├─ bindAppWidgetIdIfAllowed() == true
    │   → 바로 설정 Activity 또는 커밋
    │
    └─ bindAppWidgetIdIfAllowed() == false
        → ACTION_APPWIDGET_BIND 시스템 승인 화면
        → 승인 후 설정 Activity 또는 커밋
```

기존 `REQUEST_PICK_WIDGET` / `REQUEST_CONFIGURE_WIDGET` 흐름은 유지하되, 선택 단계만 커스텀으로 대체한다.

## 3. P1: 메인 화면 UI 재구성

### 3.1 현재 구성

```
[제목]
[설명]
[선택된 위젯 텍스트]
[위젯 선택] [선택 해제]
--- 표시 방식 ---
[오버레이 열기] [미니 런처 표시] [앱 안에서 열기] [오버레이 숨기기]
--- 선택적 시스템 표면 ---
[버블 알림 보내기] [진행 상태 테스트] [진행 상태 종료]
--- 앱 내부 위젯 미리보기 ---
[미리보기 컨테이너]
[상태 텍스트]
```

### 3.2 개선 구성

```
┌─────────────────────────────────┐
│ 위젯 오버레이                    │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 선택된 위젯: 없음            │ │ ← 카드: 위젯 정보 + 미리보기
│ │                             │ │
│ │   [위젯 미리보기 영역]       │ │
│ │                             │ │
│ │ [위젯 선택]  [선택 해제]     │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 표시 방식                    │ │ ← 카드: 오버레이 관련
│ │ [오버레이 열기]              │ │
│ │ [미니 런처 표시]             │ │
│ │ [앱 안에서 열기]             │ │
│ │ [오버레이 숨기기]            │ │
│ └─────────────────────────────┘ │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 시스템 표면                  │ │ ← 카드: 선택적 기능
│ │ [버블 알림 보내기]           │ │
│ │ [진행 상태 테스트]           │ │
│ │ [진행 상태 종료]             │ │
│ └─────────────────────────────┘ │
│                                 │
│ 현재 상태: 위젯을 선택하면...    │ ← 하단 상태
└─────────────────────────────────┘
```

**변경 사항:**
- 미리보기를 위젯 정보 카드 안으로 이동 (선택 즉시 보이도록)
- 버튼을 카드형 섹션으로 그룹화
- 각 섹션에 아이콘 추가 (Material Icons)
- 불필요한 반복 텍스트 제거

## 4. P2: 시각적 피드백

- 위젯 선택 상태: 앱 아이콘 + 위젯 이름 + 크기 표시
- 오버레이 권한 상태: 아이콘으로 표시 (허용/미허용)
- 버튼 비활성화: 위젯 미선택 시 관련 버튼 회색 처리

## 5. 구현 순서

| 단계 | 내용 | 변경 파일 |
|---|---|---|
| 1 | `WidgetPickerActivity` 생성 (커스텀 위젯 선택기) | 신규: `WidgetPickerActivity.kt` |
| 2 | `WidgetHostController`에서 커스텀 선택기 호출로 변경 | 수정: `WidgetHostController.kt` |
| 3 | 매니페스트에 Activity 등록 | 수정: `AndroidManifest.xml` |
| 4 | 메인 화면 UI 카드형 재구성 | 수정: `MainActivity.kt` |
| 5 | 문자열 리소스 추가 | 수정: `strings.xml` |
| 6 | 빌드·테스트 검증 | — |

## 6. 위험 요소

| 위험 | 대응 |
|---|---|
| `bindAppWidgetIdIfAllowed()` 거부 | 시스템 `ACTION_APPWIDGET_BIND` 폴백 |
| `loadPreviewImage()` null 반환 | `initialLayout` inflate로 대체 |
| 위젯 수가 매우 많은 경우 (100+) | 앱별 접기/펼치기 + 검색 |
| 기존 위젯 선택·저장 흐름 호환 | `WidgetRepository` 인터페이스 유지 |
