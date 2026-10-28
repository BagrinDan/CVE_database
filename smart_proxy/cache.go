package main

import (
	"log"
	"os"
	"strconv"

	"github.com/go-redis/redis/v8"
	"github.com/joho/godotenv"
)


func initRedis() *redis.Client {
	if err := godotenv.Load("../.env"); err != nil {
		log.Println("Файл .env не найден, используются переменные окружения ОС")
	}

	addr := os.Getenv("REDIS_ADDRES")
	password := os.Getenv("REDIS_PASSWORD")
	dbStr := os.Getenv("REDIS_DB")
	log.Printf("Addres: %v, password: %v, dbStr: %v", addr, password, dbStr)

	db, err := strconv.Atoi(dbStr)
	if err != nil {
		db = 0 
	}

	return redis.NewClient(&redis.Options{
		Addr:     addr,
		Password: password,
		DB:       db,
	})
}