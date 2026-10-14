# Clean vendor base URLs: https://<vendor>.<LAB_GATEWAY_DOMAIN>/<path>.
location / {
    if ($vendor_api_key = "") {
        return 404 '{"error":"Not Found","message":"Unknown vendor host"}';
    }
    limit_req zone=lab burst=20 nodelay;
    set $lab_service lab-service:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Lab-Original-Uri $request_uri;
    proxy_read_timeout 120s;
    add_header X-Lab-Upstream lab-service always;
    proxy_pass http://$lab_service/itbsrv/api/http/$vendor_api_key$request_uri;
}
