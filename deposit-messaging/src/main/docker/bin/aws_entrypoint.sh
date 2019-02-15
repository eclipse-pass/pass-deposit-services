#!/bin/sh

aws s3 cp s3://pass-configuration-files/entrypoints/depositservices.sh /bin/depositservices_entrypoint.sh

if [ -f /bin/depositservices_entrypoint.sh ]; then
    echo "depositservices_entrypoint.sh found...running"
    chmod 700 /bin/depositservices_entrypoint.sh
    /bin/depositservices_entrypoint.sh
else
    java -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -jar deposit-messaging.jar listen
fi

