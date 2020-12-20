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


user=$(whoami)

echo "Executing java"

printf "%.2d.. " 0

user=$(id -u):$(id -g)

node=$(nextnode 0)
oarsh -n $node docker exec -d node-00 ./start.sh 0 $user "$@"

sleep 1

for i in $(seq 01 $(($nNodes - 1))); do
  node=$(nextnode $i)
  ii=$(printf "%.2d" $i)
  echo -n "$ii.. "
  if [ $((($i + 1) % 10)) -eq 0 ]; then
    echo ""
  fi
  c=$(($i-1))
  cc=$(printf "%.2d" $c)
  
  oarsh -n $node docker exec -d node-${ii} ./start.sh $i $user "$@" contact=node-${cc}:10000
  sleep 0.5
done

sleep 120

for i in $(seq 00 $(($nNodes - 1))); do
  node=$(nextnode $i)
  ii=$(printf "%.2d" $i)
  echo -n "$ii.. "
  if [ $((($i + 1) % 10)) -eq 0 ]; then
    echo ""
  fi
  
  oarsh -n $node docker exec -d node-${ii} killall java
done

echo ""
