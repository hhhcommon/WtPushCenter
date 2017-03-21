echo start push
_heapMem="-Xms512m -Xmx1024m"
nohup java $_heapMem -jar PushCenter.jar > ./logs/push.log 2>&1 &
