package com.example.multas.domain.port;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;

/**
 * Puerto de salida (driven port) — Java puro, sin ninguna dependencia de Spring.
 * Cualquier pasarela de pago externa implementa este contrato para que
 * MultaService no conozca los detalles HTTP de ningun proveedor concreto.
 */
public interface PasarelaPagoPort {
    ResultadoPago procesar(Multa multa);
}
