FROM tomcat:10.1-jdk17-temurin

# host 네트워크로 실행한다(호스트 PostgreSQL 127.0.0.1:5432 접속). 주소·포트는 CATALINA_OPTS 로 정한다.
# 운영 127.0.0.1:8082 (nginx /blog/ 가 프록시), 미리보기는 -Dtomcat.port 만 바꿔 띄운다.
# 종료 포트(8005)는 끈다 — host 네트워크라 같은 서버의 다른 Tomcat 과 겹치고, 컨테이너는 신호로 종료한다
ENV CATALINA_OPTS="-Dtomcat.address=127.0.0.1 -Dtomcat.port=8082"
RUN sed -i -e 's/<Connector port="8080"/<Connector address="${tomcat.address}" port="${tomcat.port}"/' \
           -e 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml \
    && grep -q '<Server port="-1"' /usr/local/tomcat/conf/server.xml \
    && grep -q 'port="${tomcat.port}"' /usr/local/tomcat/conf/server.xml

RUN rm -rf /usr/local/tomcat/webapps/*
COPY target/blog.war /usr/local/tomcat/webapps/blog.war

ENV TZ=Asia/Seoul
EXPOSE 8082

CMD ["catalina.sh", "run"]
