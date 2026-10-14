# Raw ITB receive URLs: /itbsrv/api/http/<system API key>/<uriExtension>.
location /itbsrv/api/http/ {
    limit_req zone=lab burst=20 nodelay;
    set $lab_service lab-service:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Lab-Original-Uri $request_uri;
    proxy_read_timeout 120s;
    add_header X-Lab-Upstream lab-service always;
    proxy_pass http://$lab_service$request_uri;
}
