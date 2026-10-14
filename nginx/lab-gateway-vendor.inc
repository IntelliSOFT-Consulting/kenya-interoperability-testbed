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
    # Re-send the headers the suite checks under canonical names: ITB keeps
    # header names as received, so "Content-type" or "X-facility-id" would be
    # missed. nginx $http_* lookups ignore case; an empty value drops the header.
    proxy_set_header Content-Type $content_type;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header X-Facility-Id $http_x_facility_id;
    proxy_set_header X-Facility-Id-Type $http_x_facility_id_type;
    proxy_read_timeout 120s;
    add_header X-Lab-Upstream lab-service always;
    proxy_pass http://$lab_service/itbsrv/api/http/$vendor_api_key$request_uri;
}
