## 📜 Convention

### Commit Convention

---

<details>
<summary style="font-size:1.3em;">Rules</summary>
<div markdown="1">

### 1. Git Flow

작업 순서는 다음과 같습니다.

1. GitHub issue를 생성하고 assignee, milestone, label, project를 설정합니다.
2. 최신 `dev` branch에서 작업 유형에 맞는 branch를 생성합니다.
3. 작업 및 필요한 검증을 수행합니다.
4. add → commit → push → pull request 순서로 진행합니다.
5. self review와 CodeRabbit 리뷰를 확인하고 필요한 수정을 반영합니다.
6. pull request를 `dev` branch로 squash merge 합니다.
7. 완료된 issue와 pull request를 정리하고 작업 branch를 삭제합니다.
8. 다음 작업은 최신 `dev` branch에서 새로운 branch를 생성합니다.

진행 중인 다른 작업에 `dev`의 변경이 필요한 경우 해당 변경을 작업 branch에 반영합니다.

### 2. Etc

- `dev` branch에서 직접 작업하는 것은 원칙적으로 금지합니다. 단, README 작성은 `dev` branch에서 수행합니다.
- `dev`, `main` branch로 merge할 때에는 pull request를 생성합니다.
- 변경 내용에 필요한 검증을 수행하고 실제 확인 결과를 기록합니다.
- merge된 작업 branch를 다른 issue에 재사용하지 않습니다.
- 서로 관련된 변경은 함께 검토·검증할 수 있는 범위에서 하나의 pull request로 묶을 수 있습니다.

</div>
</details>

<details>
<summary style="font-size:1.3em;">Branch</summary>
<div markdown="1">

### 1. Branch

branch는 issue를 기반으로 작업 단위 또는 기능 단위로 생성합니다.

도메인이 같더라도 다른 작업이라면 별도 branch를 생성합니다.

### 2. Branch Naming Rule

branch를 생성하기 전에 issue를 먼저 작성합니다.

생성된 issue 번호와 domain 명을 조합하여 다음 형식을 사용합니다.

```text
<Prefix>/<Issue_Number>-<Domain>
```

- Prefix와 Domain은 소문자를 사용합니다.
- 여러 단어로 구성된 Domain은 하이픈(`-`)으로 구분합니다.
- release branch는 예외적으로 `release/<버전>` 형식을 사용합니다.

### 3. 기준 Branch

| Branch | 설명 |
| --- | --- |
| `main` | 개발이 완료된 배포 대상 산출물을 관리합니다. |
| `dev` | 작업 branch의 변경 사항을 통합합니다. 일반 작업 PR의 대상 branch입니다. |

### 4. 작업 Branch Prefix

| Prefix | 형식 | 설명 | 예시 |
| --- | --- | --- | --- |
| `feature` | `feature/<Issue_Number>-<Domain>` | 새로운 기능 개발 | `feature/7-auth` |
| `bugfix` | `bugfix/<Issue_Number>-<Domain>` | 버그 수정 | `bugfix/3-budget` |
| `hotfix` | `hotfix/<Issue_Number>-<Domain>` | 긴급 버그 수정 | `hotfix/15-auth` |
| `release` | `release/<버전>` | 배포 준비 | `release/v1.0.0` |
| `chore` | `chore/<Issue_Number>-<Domain>` | 설정 및 유지보수 | `chore/5-repository` |
| `docs` | `docs/<Issue_Number>-<Domain>` | 문서 작업 | `docs/8-convention` |
| `style` | `style/<Issue_Number>-<Domain>` | 코드 스타일 변경 | `style/9-format` |
| `refactor` | `refactor/<Issue_Number>-<Domain>` | 코드 구조 개선 | `refactor/10-budget` |

### 5. Branch와 Commit Prefix 구분

branch Prefix와 commit·PR Prefix는 구분합니다.

- `feature` branch → `[feat]`
- `bugfix`, `hotfix` branch → `[fix]`
- 나머지는 실제 변경 내용에 맞는 commit Prefix를 사용합니다.

예시:

```text
Branch: bugfix/3-budget
Commit: [fix] #3 고정지출 예산 입력 화면 연결
PR:     [fix] #3 고정지출 예산 입력 화면 연결
```

</div>
</details>

<details>
<summary style="font-size:1.3em;">Issue</summary>
<div markdown="1">

### 1. Issue

작업 시작 전 GitHub issue를 생성합니다.

issue는 작업 단위 또는 기능 단위로 생성하며, 생성 후 표시되는 issue number를 branch 이름과 commit message에 사용합니다.

issue 제목에는 작업의 대표적인 설명을 적고, 내용에는 배경·목적·작업 목록·검증 계획·완료 조건을 작성합니다.

issue 생성 시 다음 항목을 설정합니다.

- Assignee: 작업 담당자
- Label: 작업 유형 및 우선순위
- Milestone: 해당 작업의 목표 일정
- Project: 작업을 관리하는 프로젝트

이슈 폼의 작업 유형 선택과 실제 GitHub label 부착은 별도로 확인합니다.

### 2. Issue Naming Rule

```text
[<Prefix>] <Description>
```

Prefix는 commit message convention을 따릅니다.

### 3. Examples

```text
[feat] 월 식비 예산 설정 기능 구현
[fix] 고정지출 예산 입력 화면 연결
[chore] CodeRabbit 리뷰 설정
[docs] 프로젝트 컨벤션 정리
```

</div>
</details>

<details>
<summary style="font-size:1.3em;">Commit</summary>
<div markdown="1">

### 1. Commit Message Convention

다음 형식을 준수합니다.

```text
[<Prefix>] #<Issue_Number> <Description>
```

- **feat**: 새로운 기능 구현  
  `[feat] #11 구글 로그인 API 기능 구현`
- **fix**: 코드 오류 수정  
  `[fix] #10 회원가입 비즈니스 로직 오류 수정`
- **del**: 쓸모없는 코드 삭제  
  `[del] #12 불필요한 import 제거`
- **docs**: README나 wiki 등의 문서 개정  
  `[docs] #14 리드미 수정`
- **refactor**: 내부 로직은 변경하지 않고 기존 코드를 개선하는 리팩터링  
  `[refactor] #15 코드 로직 개선`
- **chore**: 의존성 추가, yml 추가와 수정, 패키지 구조 변경, 파일 이동  
  `[chore] #21 yml 수정`  
  `[chore] #22 lombok 의존성 추가`
- **test**: 테스트 코드 작성 및 수정  
  `[test] #20 로그인 API 테스트 코드 작성`
- **style**: 코드 동작에 영향을 주지 않는 주석, 포맷, 줄바꿈 변경  
  `[style] #23 코드 포맷 정리`

</div>
</details>

<details>
<summary style="font-size:1.3em;">Pull Request</summary>
<div markdown="1">

### 1. Pull Request

`dev`, `main` branch로 merge할 때에는 pull request가 필요합니다.

일반 작업 branch의 PR 대상은 `dev`이며, squash merge 합니다.

### 2. Pull Request Naming Rule

```text
[<Prefix>] #<Issue_Number> <Description>
```

Prefix는 commit message convention을 따릅니다.

### 3. 작성 내용

저장소의 PR template을 사용하여 다음 내용을 작성합니다.

- 변경 내용과 목적
- 관련 issue
- 실제 수행한 검증 방법과 결과
- UI 변경 시 스크린샷 및 모바일 확인 결과
- 미확인 사항이 있다면 그 내용과 이유

### 4. Review

- PR 생성 전 변경 파일을 직접 검토합니다.
- CodeRabbit 리뷰를 확인하고 유효한 지적을 반영합니다.
- UI 변경이 없는 설정·문서 PR은 화면 검증 대신 해당 변경에 필요한 검증을 기록합니다.
- 관련 없는 변경을 하나의 PR에 섞지 않습니다.

### 5. Examples

```text
[fix] #3 고정지출 예산 입력 화면 연결
[chore] #5 CodeRabbit 리뷰 설정
```

</div>
</details>

<br>

### Code Convention

---

<details>
<summary style="font-size:1.3em;">Naming</summary>
<div markdown="1">

- 패키지 : 언더스코어(`_`)나 대문자를 섞지 않고 소문자를 사용하여 작성합니다.
- 클래스 : 클래스 이름은 명사나 명사절로 지으며, 대문자 카멜표기법(Upper camel case)을 사용합니다.
- 메서드 : 메서드 이름은 동사/전치사로 시작하며, 소문자 카멜표기법(Lower camel case)를 사용합니다. 의도가 전달되도록 최대한 간결하게 표현합니다.
- 변수 : 소문자 카멜표기법(Lower camel case)를 사용합니다.
- ENUM, 상수 : 상태를 가지지 않는 자료형이면서 `static final`로 선언되어 있는 필드일 때를 상수로 간주하며, 대문자와 언더스코어(Upper_snake_case)로 구성합니다.
- DB 테이블: 소문자와 언더스코어로(lower_snake_case) 구성합니다.
- 컬렉션(Collection): **복수형**을 사용하거나 **컬렉션을 명시합니다**. (Ex. userList, users, userMap)
- LocalDateTime: 접미사에 **Date**를 붙입니다.

</div>
</details>

<details>
<summary style="font-size:1.3em;">Comment</summary>
<div markdown="1">

### 1. 한줄 주석은 // 를 사용한다.

```java
// 하이~
```

### 2. Bracket 사용 시 내부에 주석을 작성한다.

```java
/*
   하이~!
*/
```

### 3. 주요 함수에 대한 주석

```java
/*
 * 입력 : 인덱스:Long
 * 기능 : 유저 인덱스로 db에 접근해 유저 객체를 반환한다
 * 출력 : 유저:User
 */
public User getUser(Long idx)
```

</div>
</details>

<details>
<summary style="font-size:1.3em;">Import</summary>
<div markdown="1">

### 1. 소스파일당 1개의 탑레벨 클래스를 담기

> 탑레벨 클래스(Top level class)는 소스 파일에 1개만 존재해야 한다. (탑레벨 클래스 선언의 컴파일타임 에러 체크에 대해서는 Java Language Specification 7.6 참조)

### 2. static import에만 와일드 카드 허용

> 클래스를 import할 때는 와일드카드(`*`) 없이 모든 클래스명을 다 쓴다. static import에서는 와일드카드를 허용한다.

### 3. 애너테이션 선언 후 새줄 사용

> 클래스, 인터페이스, 메서드, 생성자에 붙는 애너테이션은 선언 후 새줄을 사용한다. 이 위치에서도 파라미터가 없는 애너테이션 1개는 같은 줄에 선언할 수 있다.

### 4. 배열에서 대괄호는 타입 뒤에 선언

> 배열 선언에 오는 대괄호(`[]`)는 타입의 바로 뒤에 붙인다. 변수명 뒤에 붙이지 않는다.

### 5. `long`형 값의 마지막에 `L`붙이기

> long형의 숫자에는 마지막에 대문자 'L'을 붙인다. 소문자 'l'보다 숫자 '1'과의 차이가 커서 가독성이 높아진다.

</div>
</details>

<details>
<summary style="font-size:1.3em;">URL</summary>
<div markdown="1">

### URL

URL은 RESTful API 설계 가이드에 따라 작성합니다.

- HTTP Method로 구분할 수 있는 get, put 등의 행위는 URL에 표현하지 않습니다.
- 마지막에 `/`를 포함하지 않습니다.
- `_` 대신 `-`를 사용합니다.
- 소문자를 사용합니다.
- 확장자는 포함하지 않습니다.

</div>
</details>
