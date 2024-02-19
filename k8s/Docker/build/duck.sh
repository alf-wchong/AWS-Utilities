#!/bin/bash
current=""
while true; do
    DNSName=$(aws elbv2 describe-load-balancers --query \ 'LoadBalancers[?starts_with(LoadBalancerName,`acs-alfresco-alb`) == `true`]'\ | jq -r .[].DNSName)
    latest=$(sed '2p;d' <<< $(dig +short $DNSName))
    echo $latest
    echo "public-ipv4=$latest"
        if [ "$current" == "$latest" ]
        then
                echo "ip not changed"
        else
                echo "ip has changed - updating"
                export current=$latest
                echo url="https://www.duckdns.org/update?domains=$SUBDOMAIN&token=$TOKEN&ip=$current" | curl -k -o /duck.log -K -
        fi
        sleep 5m
done
