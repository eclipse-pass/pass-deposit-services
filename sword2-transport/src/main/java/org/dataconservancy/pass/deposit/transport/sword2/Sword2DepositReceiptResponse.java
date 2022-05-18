/*
 * Copyright 2018 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dataconservancy.pass.deposit.transport.sword2;

import org.dataconservancy.pass.deposit.transport.TransportResponse;
import org.swordapp.client.DepositReceipt;

/**
 * Implements a {@link TransportResponse} composed by a {@link DepositReceipt SWORD v2 Deposit Reciept}.  Specifically
 * this class will consider a SWORD v2 deposit successful if the receipt reports that the deposit was <em>accepted</em>
 * by the remote system (i.e. an http response code in the range {@code 200}-{@code 299}).  Other components of the
 * system will be responsible for polling the {@code atom:link} that references the SWORD Statement for the
 * ultimate deposit status.
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 * @see
 * <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#depositreceipt">SWORD v2 Profile: Deposit Receipt</a>
 * @see <a href="http://swordapp.github.io/SWORDv2-Profile/SWORDProfile.html#statement">SWORD v2 Profile: Statement</a>
 */
public class Sword2DepositReceiptResponse implements TransportResponse {

    private DepositReceipt receipt;

    public Sword2DepositReceiptResponse(DepositReceipt receipt) {
        if (receipt == null) {
            throw new IllegalArgumentException("Deposit receipt must not be null.");
        }
        this.receipt = receipt;
    }

    @Override
    public boolean success() {
        return receipt.getStatusCode() > 199 && receipt.getStatusCode() < 300;
    }

    @Override
    public Throwable error() {
        return null;
    }

    public DepositReceipt getReceipt() {
        return receipt;
    }
}
