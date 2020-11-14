Usage () { 
	echo " USAGE: ./deploy/runAll.sh"
	exit 1
}  

./deploy/setup.sh 100

mkdir -p Logs-Cluster

#ALL COMBINATIONS
echo "################## EAGER + HYPARVIEW..."
./deploy/runTest.sh --broadcast=eager --membership=hyparview --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership=hyparview --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=eager --membership=hyparview --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership=hyparview --payload_size=10000 --broadcast_interval=3000 

echo "################## EAGER + CYCLON..."
./deploy/runTest.sh --broadcast=eager --membership=cyclon --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership=cyclon --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=eager --membership=cyclon --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership=cyclon --payload_size=10000 --broadcast_interval=3000 

echo "################## PLUMTREE + CYCLON..."
./deploy/runTest.sh --broadcast=plumtree --membership=cyclon --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership=cyclon --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=plumtree --membership=cyclon --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership=cyclon --payload_size=10000 --broadcast_interval=3000 

echo "################## PLUMTREE + HYPARVIEW..."
./deploy/runTest.sh --broadcast=plumtree --membership=hyparview --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership=hyparview --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=plumtree --membership=hyparview --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership=hyparview --payload_size=10000 --broadcast_interval=3000 

#SINGLE COMBINATIONS
echo "################## HYPARVIEW..."
./deploy/runTest.sh --broadcast="" --membership=hyparview --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast="" --membership=hyparview --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast="" --membership=hyparview --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast="" --membership=hyparview --payload_size=10000 --broadcast_interval=3000 

echo "################## CYCLON..."
./deploy/runTest.sh --broadcast="" --membership=cyclon --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast="" --membership=cyclon --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast="" --membership=cyclon --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast="" --membership=cyclon --payload_size=10000 --broadcast_interval=3000 

echo "################## PLUMTREE..."
./deploy/runTest.sh --broadcast=plumtree --membership="" --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership="" --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=plumtree --membership="" --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=plumtree --membership="" --payload_size=10000 --broadcast_interval=3000 

echo "################## EAGER..."
./deploy/runTest.sh --broadcast=eager --membership="" --payload_size=10 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership="" --payload_size=10 --broadcast_interval=3000 
./deploy/runTest.sh --broadcast=eager --membership="" --payload_size=10000 --broadcast_interval=1000 
./deploy/runTest.sh --broadcast=eager --membership="" --payload_size=10000 --broadcast_interval=3000 
