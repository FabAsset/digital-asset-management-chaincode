package kr.ac.postech.sslab.fabasset.chaincode.protocol;

import com.google.protobuf.ByteString;
import kr.ac.postech.sslab.fabasset.chaincode.constant.DataType;
import kr.ac.postech.sslab.fabasset.chaincode.manager.TokenManager;
import kr.ac.postech.sslab.fabasset.chaincode.manager.TokenTypeManager;
import kr.ac.postech.sslab.fabasset.chaincode.client.Address;
import kr.ac.postech.sslab.fabasset.chaincode.util.DataTypeConversion;
import org.hyperledger.fabric.shim.ChaincodeStub;

import java.io.IOException;
import java.util.*;

import static kr.ac.postech.sslab.fabasset.chaincode.constant.Key.HASH_KEY;
import static kr.ac.postech.sslab.fabasset.chaincode.constant.Key.PATH_KEY;

public class Extension {
    private static final String QUERY_OWNER_AND_TYPE = "{\"selector\":{\"owner\":\"%s\",\"type\":\"%s\"}}";

    public static long balanceOf(ChaincodeStub stub, String owner, String type) {
        String query = String.format(QUERY_OWNER_AND_TYPE, owner, type);
        return Default.queryByValues(stub, query).size();
    }

    public static List<String> tokenIdsOf(ChaincodeStub stub, String owner, String type) {
        String query = String.format(QUERY_OWNER_AND_TYPE, owner, type);
        return Default.queryByValues(stub, query);
    }

    public static boolean mint(ChaincodeStub stub, String id, String type, Map<String, Object> xattr, Map<String, String> uri) throws IOException {
        String caller = Address.getMyAddress(stub);

        TokenTypeManager manager = TokenTypeManager.load(stub);
        Map<String, List<String>> attributes = manager.getType(type);
        if (attributes == null) {
            return false;
        }

        if (xattr == null) {
            xattr = new HashMap<>();
        }

        if (!hasValidXAttr(xattr, attributes)) {
            return false;
        }

        if (!initXAttr(xattr, attributes)) {
            return false;
        }

        if (!hasValidURI(uri)) {
            return false;
        }

        TokenManager nft = new TokenManager();

        if (nft.hasToken(stub, id)) {
            return false;
        }

        nft.setId(id);
        nft.setType(type);
        nft.setOwner(caller);
        nft.setApprovee("");
        nft.setXAttr(xattr);
        nft.setURI(uri);
        nft.store(stub);

        ERC721.eventTransfer(stub, "", caller, id);

        return true;

    }

    private static boolean hasValidXAttr(Map<String, Object> validated, Map<String, List<String>> validator) {
        for (String key : validated.keySet()) {
            if (!validator.containsKey(key)) {
                return false;
            }
        }

        return true;
    }

    private static boolean initXAttr(Map<String, Object> dest, Map<String, List<String>> src) {
        for (Map.Entry<String, List<String>> entry : src.entrySet()) {
            if(!insertNewEntry(entry.getKey(), entry.getValue(), dest)) {
                return false;
            }
        }

        return true;
    }

    private static boolean insertNewEntry(String attribute, List<String> info, Map<String, Object> dest) {
        if (info.size() != 2) {
            return false;
        }

        if (!dest.containsKey(attribute)) {
            String dataType = info.get(0);
            Object initialValue = DataTypeConversion.strToDataType(dataType, info.get(1));
            if (initialValue == null) {
                return false;
            }

            dest.put(attribute, initialValue);
        }

        return true;
    }

    private static boolean hasValidURI(Map<String, String> uri) {
        if (uri == null) {
            uri = new HashMap<>();
            uri.put(HASH_KEY, "");
            uri.put(PATH_KEY, "");

            return true;
        }

        return uri.keySet().size() == 2 && uri.containsKey(PATH_KEY) && uri.containsKey(HASH_KEY);
    }

    private static void eventExtensibleAttribute(ChaincodeStub stub, String id, String index, String value) {
        String message = String.format("Update attribute %s to %s in Token %s", index, value, id);
        stub.setEvent("ExtensibleAttribute", ByteString.copyFromUtf8(message).toByteArray());
    }

    public static boolean setURI(ChaincodeStub stub, String id, String index, String value) throws IOException {
        TokenManager nft = TokenManager.load(stub, id);
        Map<String, String> uri = nft.getURI();
        if (!uri.containsKey(index)) {
            return false;
        }

        nft.setURI(index, value);
        nft.store(stub);

        eventExtensibleAttribute(stub, id, index, value);

        return false;
    }

    public static String getURI(ChaincodeStub stub, String id, String index) throws IOException {
        TokenManager nft = TokenManager.load(stub, id);
        Map<String, String> uri = nft.getURI();
        if (!uri.containsKey(index)) {
            return null;
        }

        return nft.getURI(index);
    }

    public static boolean setXAttr(ChaincodeStub stub, String id, String index, String value) throws IOException {
        TokenManager nft = TokenManager.load(stub, id);
        Map<String, Object> xattr = nft.getXAttr();
        if (!xattr.containsKey(index)) {
            return false;
        }

        TokenTypeManager manager = TokenTypeManager.load(stub);
        List<String> info = manager.getAttribute(nft.getType(), index);
        String dataType = info.get(0);
        Object object = DataTypeConversion.strToDataType(dataType, value);
        if (object == null) {
            return false;
        }

        nft.setXAttr(index, object);
        nft.store(stub);

        eventExtensibleAttribute(stub, id, index, value);

        return true;
    }

    @SuppressWarnings("unchecked")
    public static String getXAttr(ChaincodeStub stub, String tokenId, String index) throws IOException {
        TokenManager nft = TokenManager.load(stub, tokenId);
        Map<String, Object> xattr = nft.getXAttr();
        if (!xattr.containsKey(index)) {
            return null;
        }

        Object value = nft.getXAttr(index);

        TokenTypeManager manager = TokenTypeManager.load(stub);
        List<String> info = manager.getAttribute(nft.getType(), index);

        if (info.isEmpty()) {
            return null;
        }

        switch (info.get(0)) {
            case DataType.INTEGER:
                return Integer.toString((int) value);

            case DataType.DOUBLE:
                return Double.toString((double) value);

            case DataType.BYTE:
                return Byte.toString((byte) value);

            case DataType.STRING:
                return (String) value;

            case DataType.BOOLEAN:
                return Boolean.toString((boolean) value);

            case DataType.LIST_INTEGER:
                List<Integer> integers = (List<Integer>) value;
                return integers != null ? integers.toString() : null;

            case DataType.LIST_DOUBLE:
                List<Double> doubles = (List<Double>) value;
                return doubles != null ? doubles.toString() : null;

            case DataType.LIST_BYTE:
                List<Byte> bytes = (List<Byte>) value;
                return bytes != null ? bytes.toString() : null;

            case DataType.LIST_STRING:
                List<String> strings = (List<String>) value;
                return strings != null ? strings.toString() : null;

            case DataType.LIST_BOOLEAN:
                List<Boolean> booleans = (List<Boolean>) value;
                return booleans != null ? booleans.toString() : null;

            default:
                return null;
        }
    }
}
