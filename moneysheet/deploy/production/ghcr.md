# Private GHCR 게시 (BE #60)

## 실행 순서

1. 이 PR을 기본 브랜치에 반영해야 `workflow_dispatch`가 등록됩니다. 기본 브랜치가 `main`이면 먼저 정상 PR 절차로 워크플로를 main에도 반영합니다. 임시 공개 게시나 PR 이벤트로 우회하지 않습니다.
2. `dev`의 **게시할 커밋**에 `BE tests` push CI가 성공했는지 확인합니다.
3. Actions → `BE private images` → Run workflow → **dev**를 선택합니다. 실행 시작 시점의 전체 SHA를 checkout하며, 해당 SHA의 push CI 성공만 인정합니다. 이후 dev가 변경되어도 이 실행의 소스는 바뀌지 않습니다.
4. 모든 타깃의 로컬 빌드가 끝난 뒤에만 GHCR 로그인을 합니다. `linux/amd64`, 전체 SHA 태그와 OCI source/revision 라벨을 사용합니다.
5. 게시 후 Private 여부·digest pull·revision을 확인하고 성공한 실행의 Summary 및 `images.env` artifact를 확인합니다. 운영에서는 태그 대신 이 **digest**를 사용합니다.

## 권한과 실패 대응

- 패키지 게시에는 job 범위 `packages: write`의 단기 `GITHUB_TOKEN`만 사용합니다. 앱 비밀값·SSH 키·장기 쓰기 PAT는 Actions에 등록하지 않습니다.
- 최초 패키지는 GHCR 기본값 Private으로 생성됩니다. 기존 Public 패키지이면 게시 전 중단하고, 게시 후에도 Private을 확인합니다. 패키지 설정 변경으로 공개되지 않도록 소유자가 관리해야 합니다.
- 패키지 API 403은 실패입니다. 404는 최초 게시 전만 허용하며, 기존 패키지가 있으나 권한이 없는 경우 패키지의 Manage Actions access에 이 저장소를 추가하고 재실행합니다.
- CI 미완료/실패·빌드·권한·게시·검증 실패는 전체 실행 실패입니다. 재실행은 동일 dev SHA의 검증 성공이 필요합니다. 일부 이미지가 이미 올라갔더라도 실패한 실행은 배포 대상으로 쓰지 않습니다.
- PR에서는 게시하지 않습니다. 수동 실행도 dev만 허용합니다. 자동 배포·서비스 기동·DB 변경·방화벽 변경은 없습니다.
- GitHub Artifact 보존 기간은 90일입니다. 실제 사용한 digest는 운영 서버 releases 기록에도 보관합니다.

## 검증 범위

PR 단계에서는 정책 단위 테스트·워크플로 구문 검사를 수행합니다. 실제 GHCR 게시/Private 확인/인증된 pull은 워크플로가 기본 브랜치에 반영된 뒤 수동 실행으로 확인해야 하며, PR 작성 시 성공했다고 간주하지 않습니다.

## 기준 문서

- https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

## Ubuntu 읽기 전용 인증 및 pull

이 절차는 **게시 성공 후** 수행합니다. FE와 BE 워크플로가 모두 성공했고 Packages 화면에서 세 패키지가 Private인지 먼저 확인합니다. BE와 migration은 반드시 같은 성공 실행의 artifact에서 선택합니다.

1. GitHub에서 서버 전용 PAT **classic**을 만들고 `read:packages`만 선택합니다. 만료일을 정하고 다운로드 권한이 있는 계정을 사용합니다. 기존 개발용 Git 자격 증명·쓰기 PAT를 서버에 복사하지 않습니다. 토큰은 채팅이나 shell 명령 인자에 입력하지 않습니다.
2. 이 디렉토리의 `scripts/registry-login.sh`, `scripts/pull-images.sh`를 Ubuntu에 복사한 뒤 SSH 터미널에서 실행합니다. 실제 토큰은 비표시 프롬프트에 직접 붙여넣습니다.

```bash
bash scripts/registry-login.sh danielhw99
```

인증은 `~/.config/moneysheet-registry/config.json`에 저장됩니다. Docker 기본 파일 저장은 암호화가 아니므로 디렉토리 700·파일 600을 유지하고 Git/공유 백업에 포함하지 않습니다. 실제 비밀값은 이 문서·Actions·이미지에 넣지 않습니다. 회수 시 `docker --config "$HOME/.config/moneysheet-registry" logout ghcr.io` 후 GitHub에서 PAT를 폐기합니다. 갱신은 새 PAT로 위 로그인 명령을 다시 실행합니다.

3. 성공 실행의 Summary/`images.env`에서 정확한 digest 세 개를 복사합니다. 다음 명령에서 `<...>`를 실제 digest로 교체합니다. 태그나 `latest`는 받지 않습니다.

```bash
bash scripts/pull-images.sh \
  'ghcr.io/danielhw99/moneysheet-fe@sha256:<64자리 digest>' \
  'ghcr.io/danielhw99/moneysheet-be@sha256:<64자리 digest>' \
  'ghcr.io/danielhw99/moneysheet-migration@sha256:<64자리 digest>' \
  /home/danielhw99/moneysheet-production
```

스크립트는 세 이미지 pull·플랫폼·소스 라벨·BE/migration revision 일치를 검사합니다. 모두 성공한 뒤에만 운영 `.env`의 이미지 세 항목을 교체하고 `releases/`에 digest 기록과 기존 `.env` 백업을 600 권한으로 남깁니다. `.env` 백업도 비밀 파일로 취급합니다. 실패 시 운영 이미지 참조는 바뀌지 않지만 이미 다운로드한 이미지는 로컬에 남을 수 있습니다.

이 스크립트는 Compose 기동, DB 접속, migration, 포트 개방을 수행하지 않습니다. 운영 설정 검증과 실제 서비스 배포는 별도 단계입니다. 단순 shell의 `docker login` 기본 인증과 분리되므로 이후 수동 pull도 동일한 `--config` 옵션을 사용해야 합니다.

## 남은 실제 검증

- PR 병합 후 기본 브랜치 등록과 dev push CI 성공 확인
- FE·BE 수동 게시 성공, 세 패키지 Private 확인
- Ubuntu에서 전용 read:packages 인증 후 세 digest pull 및 releases 기록 확인

위 단계가 끝나기 전에는 실제 게시·운영 pull 완료로 표시하지 않습니다.

## PR 단계 검증 기록 (2026-11-25)

- 게시 정책 Node 테스트 9개 통과: 정확한 SHA·push CI·소유 저장소·Private·403/404 분기.
- actionlint 1.7.7로 저장소 내 워크플로 구문 검사 통과.
- Ubuntu 임시 디렉토리에서 shell 구문 및 fake Docker 기반 pull 안전성 테스트 4개 통과. 실제 레지스트리 인증·Docker pull을 대체하지 않습니다.
- 앱 소스·Dockerfile 변경 없음. 앱 전체 테스트는 기존 PR CI에서 수행합니다. 실제 게시·서버 인증·pull은 위 후속 검증으로 남습니다.

## 운영 .env 수정 시 공유 잠금

이미지 선언은 각 키에 대해 줄 시작의 단일 `KEY=value` 형식만 지원합니다. 같은 키의 `KEY: value`, `export KEY=value`, 앞 공백·구분자 앞 공백·값 없는 선언 등은 기존 정상 선언과 함께 있거나 단독이어도 거부합니다. 지원하지 않는 형식을 먼저 정리한 후 다시 실행합니다.

`pull-images.sh`는 다운로드 이후 `.env.lock`에 배타 잠금을 얻은 뒤 snapshot 읽기부터 원자 교체·기록까지 직렬화합니다. 다른 운영 설정 수정 작업도 **같은 절대 경로의 잠금**을 사용해야 합니다. 예를 들어 Ubuntu에서 다음처럼 편집합니다.

```bash
flock -x /home/danielhw99/moneysheet-production/.env.lock \
  nano /home/danielhw99/moneysheet-production/.env
```

자동화도 같은 `flock -x .../.env.lock` 아래에서 읽기와 저장을 모두 수행합니다. 잠금 파일은 삭제하거나 교체하지 않습니다. `.env` 자체는 원자 교체로 inode가 바뀌므로 잠금 대상으로 사용하지 않습니다.

교체 직전 현재 파일과 snapshot도 비교하여 변경이 감지되면 중단합니다. 공유 잠금을 따르지 않는 편집기는 비교 직후에도 쓸 수 있으므로, 이 비교만으로 모든 경쟁을 막는다고 보장하지 않습니다. 공유 잠금에 참여한 수정은 먼저 완료된 설정을 읽어 비이미지 값을 보존합니다. 충돌로 중단된 경우 임시 파일을 제거하고 성공 digest 기록을 만들지 않습니다.

리뷰 보완 검증: Ubuntu 격리 테스트 총 7개 통과(대체 선언 단독/중복 거부, snapshot 충돌 중단, 공유 잠금 대기 및 비이미지 설정 보존 포함). 실제 운영 설정은 변경하지 않았습니다.

## 기록 저장과 설정 교체 실패 처리

새 `.env`·원본 백업·digest 기록의 쓰기와 fsync를 모두 완료한 뒤 마지막에 `.env`를 원자 교체합니다. 기록의 쓰기·flush·close·fsync 또는 교체 전 검증이 실패하면 기존 이미지 참조를 유지하고 미완성 digest 기록과 임시 설정 파일을 정리합니다. 백업은 보존 목적으로 남을 수 있습니다.

이는 실행 중 감지한 파일 작업 오류에 대한 처리입니다. 여러 파일 전체의 원자성이나 정전·강제 종료 시 자동 복구를 보장하지 않습니다. 비정상 종료 후 남은 digest 기록은 실제 `.env`와 일치하는지 확인한 뒤 사용합니다.
