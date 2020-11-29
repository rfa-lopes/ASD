nNodes=$1
shift

n_nodes=$(uniq $OAR_FILE_NODES | wc -l)

function nextnode {
  local idx=$(($1 % n_nodes))
  local i=0
  for host in $(uniq $OAR_FILE_NODES); do
    if [ $i -eq $idx ]; then
      echo $host
      break;
    fi
    i=$(($i +1))
  done
}

echo "Compiling"
mvn package

docker network rm leitaonet
echo "Killing everything"

for node in $(oarprint host); do
    oarsh $node 'docker kill $(docker ps -q)' &
done

wait

for node in $(oarprint host); do
    oarsh $node "docker swarm leave -f" 
done

docker swarm init
JOIN_TOKEN=$(docker swarm join-token manager -q)

host=$(hostname)
for node in $(oarprint host); do
  if [ $node != $host ]; then
    oarsh $node "docker swarm join --token $JOIN_TOKEN $host:2377"
  fi
done

echo "Rebuilding image"
for node in $(oarprint host); do
	oarsh $node "cd $(pwd);  docker build --rm -t asdproj ." &
done

wait

echo "Creating network"
docker network create -d overlay --attachable --subnet 172.10.0.0/16 leitaonet

user=$(whoami)
mkdir ~/asdLogs

echo "Launching containers"
for i in $(seq 00 $(($nNodes - 1))); do
  node=$(nextnode $i)
  
  ii=$(printf "%.2d" $i)
  echo -n "$ii - "
  
  cmd="docker run -d -t --rm \
    --privileged --cap-add=ALL \
    --mount type=bind,source=/home/$user/asdLogs,target=/code/logs \
    --net leitaonet --ip 172.10.10.${i} -h node-${ii} --name node-${ii} \
    asdproj ${i}"
  oarsh -n $node $cmd 
done

echo ""