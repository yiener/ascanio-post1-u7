package com.example.multas.infrastructure.pago;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.model.Multa;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Adaptador de salida para la pasarela institucional PagosUDES.
 * Activo cuando app.pagos.proveedor=pagosudes (valor por defecto).
 * Traduce el formato propio de PagosUDES (idTransaccion / estadoTransaccion)
 * al tipo de dominio neutral ResultadoPago.
 */
@Component
@ConditionalOnProperty(prefix = "app.pagos", name = "proveedor", havingValue = "pagosudes", matchIfMissing = true)
public class PagosUdesAdapter implements PasarelaPagoPort {

    private final RestTemplate restTemplate;

    @Value("${app.pagos.pagosudes.url:http://localhost:9001/pagosudes/transacciones}")
    private String urlPasarela;

    public PagosUdesAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public ResultadoPago procesar(Multa multa) {
        try {
            PagosUdesRequest req = new PagosUdesRequest(multa.getEstudianteId(), multa.getMonto());
            PagosUdesResponse res = restTemplate.postForObject(urlPasarela, req, PagosUdesResponse.class);
            boolean exitoso = res != null && "APROBADA".equals(res.estadoTransaccion());
            return new ResultadoPago(
                    "PAGOSUDES",
                    exitoso,
                    res != null ? res.idTransaccion() : null,
                    exitoso ? "Pago aprobado por PagosUDES" : "Transaccion rechazada por PagosUDES"
            );
        } catch (RestClientException ex) {
            return new ResultadoPago("PAGOSUDES", false, null,
                    "PagosUDES no disponible: " + ex.getMessage());
        }
    }

    private record PagosUdesRequest(String estudianteId, BigDecimal monto) {}
    private record PagosUdesResponse(String idTransaccion, String estadoTransaccion) {}
}
