Usage () { 
	echo " USAGE: ./deploy/runTest.sh --broadcast=flood --membership=hyparview --payload_size=10 --broadcast_interval=3000"  
	exit 1
}  

if [ $# -ne 4 ]; then
	Usage
	exit 1
fi

port="10000"
interface="eth0"
address="127.0.0.1"
sample_size="3"
sample_time="3000"
protocol_metrics_interval="1000"
channel_metrics_interval="-1"
#payload_size="10"
prepare_time="5"
run_time="60"
cooldown_time="5"
#broadcast_interval="3000"
#broadcast="flood"
#membership="hyparview"
activeMembershipSize="3"
passiveMembershipSize="30"
shuffleTime="15000"
joinTime="1000"
k="6"
c="1"
arwl="6"
prwl="3"
ka="3"
kp="4"
n="100"

while [ $# -gt 0 ]; do
	case "$1" in
		--payload_size=*)
payload_size="${1#*=}"
;;
--broadcast_interval=*)
broadcast_interval="${1#*=}"
;;
--broadcast=*)
broadcast="${1#*=}"
;;
--membership=*)
membership="${1#*=}"
;;
*) "Invalid argument." >&2
exit 1;
;;
esac
shift
done

configs="port=${port}
interface=${interface}
address=${address}
sample_size=${sample_size}
sample_time=${sample_time}
protocol_metrics_interval=${protocol_metrics_interval}
channel_metrics_interval=${channel_metrics_interval}
payload_size=${payload_size}
prepare_time=${prepare_time}
run_time=${run_time}
cooldown_time=${cooldown_time}
broadcast_interval=${broadcast_interval}
broadcast=${broadcast}
membership=${membership}
activeMembershipSize=${activeMembershipSize}
passiveMembershipSize=${passiveMembershipSize}
shuffleTime=${shuffleTime}
joinTime=${joinTime}
k=${k}
c=${c}
arwl=${arwl}
prwl=${prwl}
ka=${ka}
kp=${kp}
n=${n}"

echo "${configs}" > config.properties

sleep 1

./deploy/deploy.sh 100

echo "Passing logs to Logs-Cluster/ ..."

rm -R Logs-Cluster/"${rootlogsfolder}"/"${payloadLogsFolder}"/"${broadcast_intervalLogsFolder}"

rootlogsfolder="${broadcast}+${membership}"
payloadLogsFolder="payload_size-${payload_size}"
broadcast_intervalLogsFolder="broadcast_interval-${broadcast_interval}"

mkdir Logs-Cluster/"${rootlogsfolder}"
mkdir Logs-Cluster/"${rootlogsfolder}"/"${payloadLogsFolder}"
mkdir Logs-Cluster/"${rootlogsfolder}"/"${payloadLogsFolder}"/"${broadcast_intervalLogsFolder}"

cp -a ~/asdLogs/logs/ Logs-Cluster/"${rootlogsfolder}"/"${payloadLogsFolder}"/"${broadcast_intervalLogsFolder}"