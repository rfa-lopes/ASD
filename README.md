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
java -jar target/asdProj.jar -conf config.properties port=5001 contacts=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5002 contacts=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5003 contacts=127.0.0.1:5000

java -jar target/asdProj.jar -conf config.properties port=5002 contacts=127.0.0.1:5000
java -jar target/asdProj.jar -conf config.properties port=5003 contacts=127.0.0.1:5001
java -jar target/asdProj.jar -conf config.properties port=5004 contacts=127.0.0.1:5000,127.0.0.1:5001,127.0.0.1:5002
java -jar target/asdProj.jar -conf config.properties port=5005 contacts=127.0.0.1:5002,127.0.0.1:5003,127.0.0.1:5004
java -jar target/asdProj.jar -conf config.properties port=5006 contacts=127.0.0.1:5004,127.0.0.1:5005

```

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
