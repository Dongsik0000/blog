---
title: "Tomcat이 기동 직후 종료되던 포트 충돌"
slug: "ledger-troubleshooting-tomcat-shutdown-port"
category: "development"
publishedAt: "2026-10-08"
summary: "커밋 기록에 남은 Tomcat 8005 종료 포트 충돌과, 현재 Dockerfile에서 종료 포트를 비활성화한 해결을 설명합니다."
series: "ledger"
chapter: "troubleshooting"
seriesOrder: 1
---
## 해결한 문제

Ledger를 host 네트워크로 실행했을 때 같은 서버의 다른 Tomcat과 **8005 종료 포트가 겹쳐 기동 직후 종료**되는 문제가 있었습니다. 저장소의 `f134888` 커밋에 원인과 수정이 기록되어 있고, 현재 Dockerfile에는 해결한 설정이 들어 있습니다.

## HTTP 포트와 종료 포트

Ledger의 웹 요청은 8081로 받습니다. 하지만 Tomcat의 `<Server port="8005">`는 별도로 종료 명령을 받는 포트입니다. 웹 포트만 바꿔도 종료 포트는 그대로 남을 수 있습니다.

현재 Compose는 host 네트워크를 사용하므로 컨테이너가 호스트와 포트 공간을 함께 씁니다. 다른 Tomcat이 종료 포트를 사용하고 있었다면 이 포트도 겹칩니다.

## 현재 코드의 해결

`Dockerfile` (포트 변경과 확인 부분)

```dockerfile
RUN sed -i -e 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="8081"/' \
           -e 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml \
    && grep -q '<Server port="-1"' /usr/local/tomcat/conf/server.xml \
    && grep -q 'port="8081"' /usr/local/tomcat/conf/server.xml
```

종료 포트를 `-1`로 바꿔 비활성화합니다. 컨테이너는 Docker의 종료 신호로 멈추므로 이 종료 명령용 포트를 열어 둘 필요가 없습니다. 설정 파일의 변경 결과도 빌드 단계에서 확인합니다.

`-1`의 의미는 [Tomcat 10.1의 Server 설정 문서](https://tomcat.apache.org/tomcat-10.1-doc/config/server.html)에서 확인했습니다(2026-10-07). 종료 포트를 끄면 Tomcat의 종료 포트를 사용하는 `shutdown.sh`와 같은 종료 방식은 사용할 수 없으므로 컨테이너의 실행·종료 방식과 함께 읽습니다.

## 확인 방법

앱을 배포한 서버의 Ledger 저장소 루트에서 실행합니다.

```bash
sudo docker compose ps
sudo docker compose logs --tail=100 app
curl -I http://127.0.0.1:8081/p3/manifest.json
```

기동 직후 종료되지 않고 실행 상태를 유지하는지, 정적 파일이 HTTP 200으로 응답하는지 확인합니다. 현재 문제를 조사한다면 실제 로그에 포트 사용 오류가 있는지도 함께 보고, 모든 기동 실패를 이 사례로 단정하지 않습니다.

기록은 [해결 커밋](https://github.com/Dongsik0000/ledger/commit/f134888b5292ac03236c6977522ec5fbf5b7e557)과 현재 `Dockerfile`을 근거로 작성했습니다.
