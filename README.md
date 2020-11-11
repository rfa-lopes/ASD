# Algoritmos e Sistemas Distribuído

## Setup and Test

### Git Clone
```bash
git clone https://github.com/rfa-lopes/ASD.git
```

### Compile
```bash
mvn package
```

### Run
```bash
java -jar target/asdProj.jar -conf config.properties port=5000
```
```bash
java -jar target/asdProj.jar -conf config.properties port=5001 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5002 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5003 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5004 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5005 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5006 contact=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5007 contact=127.0.0.1:5000,127.0.0.1:5001,127.0.0.1:5002
```
---

## Setup and Test (Cluster)

### Path
```bash
ls ASD/
config.properties deploy docker Dockerfile .....
```

### Send files to DI Cluster
```bash
scp -P 12034 -r ASD asd04@cluster.di.fct.unl.pt:.
asd04@cluster.di.fct.unl.pt's password: d!7F6Xtqf8N=#DHx
```

### Reserve
```bash
oarsub -l nodes=2 -I
```

### Deploy
```bash
./deploy/deploy.sh 20
```

---
---

## Informação adicional

### Comandos Git
```bash
git pull origin master
git add .
git commit -m "Initial commit"
git push
git rm -r --cached Path/to/directories
```

### Autores
* Rodrigo Lopes - rfa.lopes@campus.fct.unl.pt - 50435
* Miguel Candeias - mb.candeias@campus.fct.unl.pt - 50647
* Salvador Rosa Mendes - sr.mendes@campus.fct.unl.pt - 50503
