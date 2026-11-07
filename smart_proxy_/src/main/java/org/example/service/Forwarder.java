package org.example.service;

import com.sun.net.httpserver.HttpExchange;
import org.example.models.DwResponse;

public interface Forwarder {
    DwResponse forward(HttpExchange exchange);
}
