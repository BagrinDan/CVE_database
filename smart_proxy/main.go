package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
)


func main() {
	rdb := initRedis()

	if err := rdb.Ping(context.Background()).Err(); err != nil {
		log.Fatalf("Ошибка подключения к Redis: %v", err)
	}

	finalHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fmt.Fprintln(w, "Запрос передан дальше (Cache/Balancer)")
	})

	http.Handle("/", clientManagerMiddleware(rdb, finalHandler))

	log.Println("Smart Proxy запущен на порту :8080")
	if err := http.ListenAndServe(":8080", nil); err != nil {
		log.Fatal(err)
	}
}