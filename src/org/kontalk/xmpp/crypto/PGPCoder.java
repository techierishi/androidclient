/*
 * Kontalk Android client
 * Copyright (C) 2013 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.kontalk.xmpp.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Iterator;

import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.util.CPIMMessage;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.openpgp.PGPCompressedData;
import org.spongycastle.openpgp.PGPCompressedDataGenerator;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPEncryptedDataGenerator;
import org.spongycastle.openpgp.PGPEncryptedDataList;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPLiteralData;
import org.spongycastle.openpgp.PGPLiteralDataGenerator;
import org.spongycastle.openpgp.PGPObjectFactory;
import org.spongycastle.openpgp.PGPOnePassSignatureList;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyEncryptedData;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.spongycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

import android.util.Log;


/**
 * PGP coder implementation.
 * @author Daniele Ricci
 */
public class PGPCoder implements Coder {

    /** Buffer size. It should always be a power of 2. */
    private static final int BUFFER_SIZE = 1 << 8;

    private final EndpointServer mServer;
    private final PGPPublicKey[] mRecipients;
    private final PersonalKey mKey;

    public PGPCoder(EndpointServer server, PersonalKey key, PGPPublicKey[] recipients) {
        mServer = server;
        mKey = key;
        mRecipients = recipients;
    }

    @Override
    public byte[] encryptText(String text) throws GeneralSecurityException {
        try {
            String from = mKey.getUserId(mServer.getNetwork());
            StringBuilder to = new StringBuilder();
            for (PGPPublicKey rcpt : mRecipients)
                to.append(PGP.getUserId(rcpt, mServer.getNetwork()))
                    .append("; ");

            // secure the message against the most basic attacks using Message/CPIM
            CPIMMessage cpim = new CPIMMessage(from, to.toString(), new Date(), text);
            byte[] plainText = cpim.toByteArray();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream(plainText);

            // setup data encryptor & generator
            BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
            encryptor.setWithIntegrityPacket(true);
            encryptor.setSecureRandom(new SecureRandom());

            // add public key recipients
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
            for (PGPPublicKey rcpt : mRecipients)
                encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(rcpt));

            OutputStream encryptedOut = encGen.open(out, new byte[BUFFER_SIZE]);

            // setup compressed data generator
            PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

            // setup signature generator
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator
                    (new BcPGPContentSignerBuilder(mKey.getSignKeyPair()
                        .getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1));
            sigGen.init(PGPSignature.BINARY_DOCUMENT, mKey.getSignKeyPair().getPrivateKey());

            PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
            spGen.setSignerUserID(false, mKey.getUserId(mServer.getNetwork()));
            sigGen.setUnhashedSubpackets(spGen.generate());

            sigGen.generateOnePassVersion(false)
                .encode(compressedOut);

            // Initialize literal data generator
            PGPLiteralDataGenerator literalGen = new PGPLiteralDataGenerator();
            OutputStream literalOut = literalGen.open(
                compressedOut,
                PGPLiteralData.BINARY,
                "",
                new Date(),
                new byte[BUFFER_SIZE]);

            // read the "in" stream, compress, encrypt and write to the "out" stream
            // this must be done if clear data is bigger than the buffer size
            // but there are other ways to optimize...
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buf)) > 0) {
                literalOut.write(buf, 0, len);
                sigGen.update(buf, 0, len);
            }

            in.close();
            literalGen.close();
            // Generate the signature, compress, encrypt and write to the "out" stream
            sigGen.generate().encode(compressedOut);
            compGen.close();
            encGen.close();

            return out.toByteArray();
        }

        catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        try {
            PGPObjectFactory pgpF = new PGPObjectFactory(encrypted);
            PGPEncryptedDataList enc;

            Object o = pgpF.nextObject();

            // the first object might be a PGP marker packet
            if (o instanceof PGPEncryptedDataList) {
                enc = (PGPEncryptedDataList) o;
            }
            else {
                enc = (PGPEncryptedDataList) pgpF.nextObject();
            }

            // check if secret key matches
            Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;

            // our encryption keyID
            long ourKeyID = mKey.getEncryptKeyPair().getPrivateKey().getKeyID();

            while (sKey == null && it.hasNext()) {
                pbe = it.next();

                if (pbe.getKeyID() == ourKeyID)
                    sKey = mKey.getEncryptKeyPair().getPrivateKey();
            }

            if (sKey == null)
                throw new IllegalArgumentException("Secret key for message not found.");

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            if (pbe.isIntegrityProtected()) {
                if (!pbe.verify()) {
                    throw new PGPException("Message failed integrity check");
                }
            }

            PGPObjectFactory plainFact = new PGPObjectFactory(clear);

            Object message = plainFact.nextObject();

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message;
                PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

                message = pgpFact.nextObject();
            }

            if (message instanceof PGPLiteralData) {
                PGPLiteralData ld = (PGPLiteralData) message;

                InputStream unc = ld.getInputStream();
                int ch;

                ByteArrayOutputStream out = new ByteArrayOutputStream();

                while ((ch = unc.read()) >= 0) {
                    out.write(ch);
                }

                return out.toByteArray();

            }
            else if (message instanceof PGPOnePassSignatureList) {
                throw new PGPException("Encrypted message contains a signed message - not literal data.");
            }
            else {
                throw new PGPException("Message is not a simple encrypted file - type unknown.");
            }

        }

        catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    public InputStream wrapInputStream(InputStream inputStream) throws GeneralSecurityException {
        // TODO
        return null;
        //return new CipherInputStream(inputStream, TODO);
    }

    public OutputStream wrapOutputStream(OutputStream outputStream) throws GeneralSecurityException {
        // TODO
        return null;
        //return new CipherOutputStream(outputStream, TODO);
    }

    public long getEncryptedLength(long decryptedLength) {
        // TODO
        return 0;
    }
}