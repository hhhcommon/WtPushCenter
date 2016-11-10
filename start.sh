#启动推送服务
echo start push
_heapMem="-Xms512m -Xmx1024m"
#java $_heapMem -jar PushCenter.jar
nohup java $_heapMem -jar PushCenter.jar > push.log 2>&1 &
