package com.example.multas.domain;

/**
 * Tipo de resultado neutral de dominio — ningun adaptador concreto se expone
 * hacia MultaService. El campo referenciaExterna es neutral frente a los
 * nombres propios de cada pasarela (idTransaccion en PagosUDES, reference
 * en Wompi), lo que permite que MultaService no sepa nada del proveedor
 * activo (Punto de decision 4).
 */
public record ResultadoPago(
        String proveedor,
        boolean exitoso,
        String referenciaExterna,
        String mensaje
) {}
