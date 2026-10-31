package org.example;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.exceptions.JedisException;


import java.io.IOException;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientManagerFilter implements Filter {

    private static final Logger logger = Logger.getLogger(ClientManagerFilter.class.getName());
    private final JedisPooled jedis;

    public ClientManagerFilter(JedisPooled jedis) {
        this.jedis = jedis;
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIP = httpRequest.getRemoteAddr();

        try {
            jedis.setex("client:" + clientIP, 300, Instant.now().toString());
            logger.info("Подключение клиента " + clientIP + " зафиксировано");
        } catch (JedisException e) {
            logger.log(Level.WARNING, "Ошибка сохранения клиента " + clientIP + " в Redis", e);
        }

        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException e) {
            logger.log(Level.SEVERE, "Ошибка при обработке запроса сервлетом: ", e);

            if (!httpResponse.isCommitted()) {
                try {
                    httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Не удалось отправить статус 500 клиенту", ex);
                }
            }
        }
    }
}