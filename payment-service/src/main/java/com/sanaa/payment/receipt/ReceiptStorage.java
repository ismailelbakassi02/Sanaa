package com.sanaa.payment.receipt;

import java.nio.file.Path;

/**
 * Stores receipt files outside the database. The interface keeps the local-disk implementation
 * replaceable by object storage without touching the payment workflow.
 */
public interface ReceiptStorage {

    /**
     * Writes the content and returns the location stored on the payment row. The location is
     * relative to the storage root so the row never leaks an absolute server path.
     */
    String store(String tenantId, byte[] content);

    /**
     * Resolves a stored location to a readable path, rejecting anything outside the root.
     */
    Path resolve(String relativePath);

    void delete(String relativePath);
}
