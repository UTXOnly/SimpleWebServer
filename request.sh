#!/bin/bash

for (( i=1; i<=10000; i++ )); do
    curl http://localhost:8000/metrics
done
