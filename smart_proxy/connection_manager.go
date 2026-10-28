package main

import (
	"log"
	"net/http"
	"time"

	"github.com/go-redis/redis/v8"
)

func clientManagerMiddleware(redisdb *redis.Client, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		// Saving ip that came from HTTP in variabile 'clientIP'
		clientIP := request.RemoteAddr
		
		context := request.Context()

		err := redisdb.Set(context, "client:" + clientIP, time.Now().String(), 5*time.Minute).Err()
		if err != nil {
			log.Printf("Ошибка сохранения клиента %s: %v", clientIP, err)
		} else {
			log.Printf("Подключение клиента %s зафиксировано", clientIP)
		}

		next.ServeHTTP(w, request)
	})
}