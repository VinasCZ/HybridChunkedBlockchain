#!/bin/bash
mvn clean compile assembly:single

for VAR in {1..10}
do
	echo $'\n==Test '$VAR &&
	java -jar target/HybridChunkedBlockchainTickSimulation-1.0.0-jar-with-dependencies.jar "tests/${VAR}_test.json" "tests/${VAR}_expected.txt"
done
