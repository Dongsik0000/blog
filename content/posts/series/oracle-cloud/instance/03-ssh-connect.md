---
title: "SSH 키로 서버에 접속하기"
slug: "oracle-cloud-instance-ssh-connect"
category: "development"
publishedAt: "2026-10-07"
summary: "공개 키와 개인 키의 원리를 이해하고, macOS, Linux, Windows에서 서버에 접속한 뒤 접속 설정을 편하게 저장하는 방법까지 다룹니다."
series: "oracle-cloud"
chapter: "instance"
seriesOrder: 3
---
## 이 글에서 할 일

만든 서버에 내 PC에서 원격으로 접속합니다. 이때 쓰는 도구가 **SSH**(Secure Shell)입니다. SSH로 접속하면 서버의 터미널을 내 PC에서 쓰는 것과 같습니다.

## SSH 키의 원리: 자물쇠와 열쇠

SSH 키는 두 파일이 한 쌍입니다.

| 파일 | 비유 | 어디에 두나 | 공유해도 되나 |
|---|---|---|---|
| 공개 키 (`.pub`) | 자물쇠 | 서버 | 됩니다 |
| 개인 키 | 열쇠 | 내 PC | **절대 안 됩니다** |

서버에 자물쇠(공개 키)를 걸어 두면, 그 자물쇠에 맞는 열쇠(개인 키)를 가진 사람만 들어올 수 있습니다. 비밀번호와 달리 네트워크로 열쇠 자체를 보내지 않기 때문에 안전합니다.

서버는 허용할 공개 키 목록을 `~/.ssh/authorized_keys` 파일에 보관합니다. 인스턴스를 만들 때 등록한 공개 키가 이 파일에 들어갑니다.

## 내 PC에서 직접 키 만들기 (선택)

콘솔에서 키를 만들어 받았다면 이 단계는 건너뜁니다. 직접 만들고 싶다면 터미널(Windows는 PowerShell)에서 실행합니다.

```bash
ssh-keygen -t ed25519 -C "my-laptop"
```

- `-t ed25519`: 키 종류. 짧고 안전한 최신 방식입니다.
- `-C`: 키에 붙이는 메모입니다. 어느 PC의 키인지 알아보기 쉽게 적습니다.

저장 위치를 물으면 Enter로 기본값(`~/.ssh/id_ed25519`)을 씁니다. 암호(passphrase)를 걸면 키 파일을 도난당해도 바로 쓸 수 없어 더 안전합니다. 만들어진 `~/.ssh/id_ed25519.pub`의 내용을 인스턴스를 만들 때 붙여 넣습니다.

## 접속하기

### 1단계. 개인 키 권한 설정 (macOS, Linux)

공식 튜토리얼은 개인 키 파일 권한을 먼저 바꾸라고 안내합니다.

```bash
chmod 400 ~/Downloads/ssh-key.key
```

`400`은 "나만 읽을 수 있음"입니다. 다른 사람도 읽을 수 있는 키 파일은 SSH가 위험하다고 판단해 사용을 거부합니다. 파일 경로는 실제로 저장한 위치로 바꿉니다.

### 2단계. 접속 명령

```bash
ssh -i ~/Downloads/ssh-key.key ubuntu@<공인 IP>
```

- `-i`: 사용할 개인 키 파일
- `ubuntu`: Ubuntu 이미지의 **기본 사용자 이름**입니다. 공식 문서 기준으로 Oracle Linux는 `opc`, Ubuntu는 `ubuntu`입니다.
- `<공인 IP>`: 콘솔의 인스턴스 상세 화면에 있는 공인 IP

### 3단계. 처음 접속할 때의 질문

처음 접속하면 다음과 같은 질문이 나옵니다.

```text
The authenticity of host '...' can't be established.
ED25519 key fingerprint is SHA256:....
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

"이 서버를 처음 보는데 믿어도 되나요?"라는 확인입니다. `yes`를 입력하면 서버 정보가 내 PC의 `~/.ssh/known_hosts`에 저장되고, 다음부터는 묻지 않습니다.

접속에 성공하면 프롬프트가 `ubuntu@web-01:~$`처럼 `사용자@서버이름` 형태로 바뀝니다.

### Windows에서 접속하기

Windows 10 이상에는 OpenSSH가 들어 있어 PowerShell에서 같은 `ssh` 명령을 씁니다. 공식 튜토리얼에 따르면 `chmod` 대신 파일 속성에서 권한을 정리합니다.

1. 개인 키 파일 오른쪽 클릭 → **속성** → **보안** 탭 → **고급**
2. **상속 사용 안 함**을 누르고, 내 계정만 **모든 권한**으로 남깁니다.

## 매번 긴 명령을 치지 않기: SSH 설정 파일

내 PC의 `~/.ssh/config` 파일에 접속 정보를 저장하면 짧은 이름으로 접속할 수 있습니다.

```text
Host myserver
    HostName <공인 IP>
    User ubuntu
    IdentityFile ~/.ssh/id_ed25519
```

`Host` 뒤의 이름은 마음대로 정합니다. 이제 다음 명령으로 접속합니다.

```bash
ssh myserver
```

이 이름은 VS Code의 원격 접속(Remote - SSH)이나 DBeaver의 SSH 터널에서도 그대로 쓸 수 있습니다.

## 서버의 SSH 보안 설정 확인하기

접속한 뒤 서버의 SSH 설정을 확인해 봅니다.

```bash
sudo sshd -T | grep -E '^(passwordauthentication|kbdinteractiveauthentication|pubkeyauthentication|permitrootlogin) '
```

다음과 같이 나오면 비밀번호로 접속하는 길이 모두 꺼져 있어, 키로만 접속할 수 있는 상태입니다.

```text
permitrootlogin without-password
pubkeyauthentication yes
passwordauthentication no
kbdinteractiveauthentication no
```

| 설정 | 값 | 의미 |
|---|---|---|
| `passwordauthentication` | `no` | 비밀번호로는 접속할 수 없습니다. 비밀번호 무작위 대입 공격을 원천 차단합니다 |
| `kbdinteractiveauthentication` | `no` | 서버가 질문을 보내고 답을 받는 방식(비밀번호를 이 방식으로 물을 수도 있음)도 꺼져 있습니다 |
| `pubkeyauthentication` | `yes` | 키 접속을 **허용**합니다. 다른 방식을 막는다는 뜻은 아니므로, "키로만"인지는 위의 두 `no`로 판단합니다 |
| `permitrootlogin` | `without-password` | root는 비밀번호로 접속할 수 없습니다 |

`sshd -T`는 서버 전체의 기본값을 보여 줍니다. 설정 파일에 `Match`로 시작하는 줄이 있으면 특정 사용자나 주소에만 다른 값이 적용될 수 있으므로, `sudo grep -rn '^Match' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/`로 함께 확인합니다. 아무것도 나오지 않으면 위 값이 모든 접속에 적용됩니다.

`passwordauthentication yes`가 나온다면 비밀번호 접속이 열려 있는 것입니다. 키 접속이 잘 되는 것을 먼저 확인한 뒤 `/etc/ssh/sshd_config.d/` 아래 설정 파일에서 `PasswordAuthentication no`로 바꾸고 `sudo systemctl reload ssh`로 적용합니다. 이때 **지금 연결된 SSH 창은 닫지 말고** 새 창으로 다시 접속되는지 확인합니다. 설정이 잘못되어도 기존 창으로 되돌릴 수 있습니다.

## 문제가 생겼을 때

| 메시지 | 원인과 해결 |
|---|---|
| `Permission denied (publickey)` | 키 파일이 틀렸거나 사용자 이름이 틀렸습니다. `-i` 경로와 `ubuntu@`를 확인합니다 |
| `UNPROTECTED PRIVATE KEY FILE!` | 키 파일 권한이 너무 넓습니다. `chmod 400`을 실행합니다 |
| `Connection timed out` | 보안 목록에서 22번이 막혔거나, 공인 IP가 틀렸거나, 서버가 꺼져 있습니다 |

## 정리

- 공개 키는 서버에, 개인 키는 내 PC에 둡니다. 개인 키는 절대 공유하지 않습니다.
- Ubuntu 서버의 기본 사용자는 `ubuntu`입니다.
- `~/.ssh/config`에 접속 정보를 저장하면 `ssh myserver`처럼 짧게 접속할 수 있습니다.
- 비밀번호 접속은 꺼 두고 키로만 접속합니다.
