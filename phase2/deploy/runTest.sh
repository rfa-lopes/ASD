Usage () { 
	echo " USAGE: ./deploy/runTest.sh  --agreement=paxos --prepareTime=2000 --acceptTime=2000"
	exit 1
}  

if [ $# -ne 3 ]; then
	Usage
	exit 1
fi

address="127.0.0.1"
interface="enp56s0u2u1"
port="36000"
p2p_port="33000"
initial_membership="127.0.0.1:36001,127.0.0.1:36000"
server_port="36000"
agreement="paxos"
hbtime="3000"
hbtimeout="3000"
prepareTime="3000"
acceptTime="3000"

while [ $# -gt 0 ]; do
	case "$1" in
--prepareTime*)
prepareTime="${1#*=}"
;;
--acceptTime=*)
acceptTime="${1#*=}"
;;
*) "Invalid argument." >&2
exit 1;
;;
esac
shift
done

configs="port=${port}
address=${address}
interface=${interface}
p2p_port=${p2p_port}
initial_membership=${initial_membership}
server_port=${server_port}
agreement=${agreement}
hbtime=${hbtime}
hbtimeout=${hbtimeout}
prepareTime=${prepareTime}
acceptTime=${acceptTime}"



echo "${configs}" > config.properties

sleep 1

./deploy/deploy.sh 1

echo "Passing logs to Logs-Cluster/ ..."

rootlogsfolder="${agreement}"

mkdir -p Logs-Cluster/"${rootlogsfolder}"

cp -a ~/asdLogs/logs/ Logs-Cluster/"${rootlogsfolder}"