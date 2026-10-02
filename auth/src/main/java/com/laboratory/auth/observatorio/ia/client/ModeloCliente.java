package com.laboratory.auth.observatorio.ia.client;

import java.util.List;

/**
 * Contrato unico del proveedor de modelo. Los servicios dependen de esta interfaz y
 * no de una implementacion concreta: cambiar de proveedor es cambiar
 * {@code ia.modelo.proveedor} en la configuracion, no reescribir el modulo.
 *
 * <p>La separacion entre {@link #embedDocumento} y {@link #embedConsulta} no es
 * decorativa. Los modelos de embedding se entrenan con dos roles distintos: uno para
 * el texto que se guarda y otro para el texto que busca. Mezclarlos baja la calidad
 * de la recuperacion, asi que el RAG los pide por separado.
 */
public interface ModeloCliente {

    /** HU-09: si hay clave configurada. Sin clave el modulo responde, no revienta. */
    boolean disponible();

    /**
     * Variable de entorno que lleva la clave, solo para poder nombrar al
     * administrador el sitio exacto donde configurarla.
     */
    String variableClave();

    /** Genera texto siguiendo las reglas de sistema indicadas. */
    String generar(String sistema, String usuario);

    /** Embedding del fragmento que se indexa. */
    float[] embedDocumento(String texto);

    /** Embedding de la pregunta que busca. */
    float[] embedConsulta(String texto);

    /**
     * Embeddings de los fragmentos que se indexan, en el mismo orden de la entrada.
     *
     * <p>El default hace una llamada por elemento. Un proveedor con endpoint de lote
     * lo sobrescribe: indexar un documento son decenas de fragmentos, y a una llamada
     * HTTP por fragmento el tier gratuito se agota a mitad del archivo.
     */
    default float[][] embedDocumentosLote(List<String> textos) {
        float[][] salida = new float[textos.size()][];
        for (int i = 0; i < textos.size(); i++) {
            salida[i] = embedDocumento(textos.get(i));
        }
        return salida;
    }

    /**
     * Embedding sin distinguir el rol. Los proveedores que exponen un unico modelo
     * de embeddings (OpenRouter, por ejemplo) usan el mismo endpoint para ambos, y
     * su codigo es identico: por eso el metodo abstracto son los dos roles separados.
     */
    default float[] embed(String texto) {
        return embedDocumento(texto);
    }
}