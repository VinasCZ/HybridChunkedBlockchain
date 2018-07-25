package simulatedHybridBlockchain;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class SalesTransaction extends Transaction {

	private Integer networkTick;
	private HashMap<String, String> headData = new HashMap<>();
	private HashMap<String, String> bodyData = new HashMap<>();
	private HashMap<String, String> signaturesData = new HashMap<>();
	
	public SalesTransaction(String origin, Integer networkTick) {
		super(origin, null);
		
		this.networkTick = networkTick;
		
		this.generateSalesData();
	}
	
	/**
	 * Sets only data in format specified by EET system. Ignores SOAP envelope/header/body.
	 */
	public void toXml() {
		String head = "<eet:Hlavicka ";
		String body = "<eet:Data ";
		String signatures = "<eet:KontrolniKody>";
		
		for(String key: headData.keySet()) {
			head = head.concat(key+"='"+headData.get(key)+"' ");
		}
		head = head.concat("/>");
		
		for(String key: bodyData.keySet()) {
			body = body.concat(key+"='"+bodyData.get(key)+"' ");
		}
		body = body.concat("/>");
		
		signatures = signatures.concat("<eet:pkp digest='SHA256' cipher='RSA2048' encoding='base64'>"+this.signaturesData.get("pkp")+"</eet:pkp>");
		signatures = signatures.concat("<eet:bkp digest='SHA1' encoding='base16'>"+this.signaturesData.get("bkp")+"</eet:bkp></eet:KontrolniKody>");
		
		this.payload = "<eet:Trzba>"+head+body+signatures+"</eet:Trzba>";
	}
	
	/**
	 * Sets data in condensed JSON format. Assumes document structure of {key: value, ..}
	 */
	public void toJson() {
		String ret = "{";
		
		for(String key: headData.keySet()) {
			ret = ret.concat(key+":'"+headData.get(key)+"', ");
		}
		
		for(String key: bodyData.keySet()) {
			ret = ret.concat(key+":'"+bodyData.get(key)+"', ");
		}
		
		// add ciphers and end the JSON object
		ret = ret.concat("pkp: {digest:'SHA256', cipher:'RSA2048', encoding:'base64', data:'"+this.signaturesData.get("pkp")+"'}, ");
		ret = ret.concat("bkp: {digest:'SHA1', encoding:'base16', data:'"+this.signaturesData.get("bkp")+"'}}");
		
		this.payload =  ret;
	}

	/**
	 * Generates sales data based on EET specifications (* = required):
	 * (head of transaction)
	 * UUID v4				*uuid			(id of transaction)
	 * ISO 8601 datetime	*dat_odesl 		(timestamp)
	 * boolean 				*prvni_zaslani 	(first_sent_at)
	 * boolean				overeni			(flag setting verification mode of transaction, false/missing = regular mode)
	 * 
	 * (transaction content)
	 * Str(CZ[0-9]{8,10})					*dic_popl			(VAT number of sending entity)
	 * Str(CZ[0-9]{8,10})					dic_poverujiciho 	(VAT number of entity that trusts sending entity to submit transaction)
	 * Str([1-9][0-9]{0,5})					*id_provoz			(unique id of store/location for an entity; unique in system using key (dic_popl, id_provoz) )
	 * Str([0-9a-zA-Z\.,:;/#\-_ ]{1,20})	*id_pokl 			(unique id of a cash register (e.g. 5a/A-q/5:22d_2); must follow system uniqueness using key (dic_popl, id_provoz, id_pokl, dat_trzby))
	 * Str([0-9a-zA-Z\.,:;/#\-_ ]{1,25})	*porad_cis			(unique id of a single receipt (e.g. #25/c-12/1A_2/2016); must follow system uniqueness using key (dic_popl, id_provoz, id_pokl, porad_cislo, dat_trzby))
	 * ISO 8601 datetime					*dat_trzby			(time of sales transaction)
	 * W3C XSD 1.1 decimal					*celk_trzba			(total price, e.g. "250.00", "-187.20", "0.56")
	 * W3C XSD 1.1 decimal					zakl_nepodl_dph		(amount without VAT)
	 * W3C XSD 1.1 decimal					zakl_dan1			(amount under basic VAT)
	 * W3C XSD 1.1 decimal					dan1				(VAT to pay from zakl_dan1)
	 * W3C XSD 1.1 decimal					zakl_dan2			(amount under first lowered VAT)
	 * W3C XSD 1.1 decimal					dan2				(VAT to pay from zakl_dan2)
	 * W3C XSD 1.1 decimal					zakl_dan3			(amount under second lowered VAT)
	 * W3C XSD 1.1 decimal					dan3				(VAT to pay from zakl_dan3)
	 * W3C XSD 1.1 decimal					cest_sluz			(amount for VAT concerning travel services)
	 * W3C XSD 1.1 decimal					pouzit_zboz1		(amount for VAT concerning sales of used goods, normal VAT)
	 * W3C XSD 1.1 decimal					pouzit_zboz2		(amount for VAT concerning sales of used goods, first lowered VAT)
	 * W3C XSD 1.1 decimal					pouzit_zboz3		(amount for VAT concerning sales of used goods, second lowered VAT)
	 * W3C XSD 1.1 decimal					urceno_cerp_zuct	(total amount for future usage or accounting)
	 * W3C XSD 1.1 decimal					cerp_zuct			(total amount used as future usage or accounting)
	 * int 0/1								*rezim				(type of EET accounting, 1 = regular, 0 = simple)
	 * 
	 * (signatures)
	 * W3C XSD 1.1 base64binary, 344 chars	*pkp	(signature of VAT entity -> SHA256(transaction.toString with | as divider) -> sign with RSASSA-PKCS1v1_5 (RFC3447) -> encode to base64)
	 * W3C XSD 1.1 hexBinary				*bkp	(hash digest of PKP)
	 */
	private void generateSalesData() {
		// Predictable seeder
		Random rng = new Random(this.origin.hashCode()/(this.networkTick == 0 ? 1 : this.networkTick));
		
		String rngSeed = (this.origin.hashCode()/(this.networkTick == 0 ? 1 : this.networkTick))+"";
		
		this.headData.put("uuid", UUID.nameUUIDFromBytes(rngSeed.getBytes()).toString());
		this.headData.put("dat_odesl", ZonedDateTime.of(2018, 1, 1, 12, 0, this.networkTick/1000, (this.networkTick%1000)*1000000, ZoneId.of("ECT", ZoneId.SHORT_IDS)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		this.headData.put("prvni_zaslani", ZonedDateTime.of(2018, 1, 1, 12, 0, this.networkTick/1000, (this.networkTick%1000)*1000000, ZoneId.of("ECT", ZoneId.SHORT_IDS)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
		if(rng.nextBoolean()) {
			this.headData.put("overeni", rng.nextBoolean() ? "true" : "false");
		}
		
		
		String dic_poplatnika = "CZ"+(rng.nextBoolean() ? this.generateIntegerString(rng, 8) : this.generateIntegerString(rng, 10));
		this.bodyData.put("dic_poplatnika", dic_poplatnika);
		
		if(rng.nextBoolean()) {
			this.bodyData.put("dic_poverujiciho", "CZ"+(rng.nextBoolean() ? this.generateIntegerString(rng, 8) : this.generateIntegerString(rng, 10)));
		}
		

		String idMask = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\\.,:;/#\\-_ ";
		
		this.bodyData.put("id_provoz", rng.nextInt(1000000)+"");
		this.bodyData.put("id_pokl", this.generateCharacterString(rng, 20, idMask));
		this.bodyData.put("porad_cis", this.generateCharacterString(rng, 25, idMask));
		this.bodyData.put("dat_trzby", ZonedDateTime.of(2018, 1, 1, 12, 0, this.networkTick/1000, (this.networkTick%1000)*1000000, ZoneId.of("ECT", ZoneId.SHORT_IDS)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));	

		// for ALL prices assume simple range of 0 to 25,000
		
		this.bodyData.put("celk_trzba", String.format("%.2f", rng.nextDouble()*25000));
		
		if(rng.nextBoolean()) {
			this.bodyData.put("zakl_nepodl_dph", String.format("%.2f", rng.nextDouble()*25000));
		}
		
		if(rng.nextBoolean()) {
			double vat = rng.nextDouble()*25000;
			this.bodyData.put("zakl_dan1", String.format("%.2f", vat));
			this.bodyData.put("dan1", String.format("%.2f", vat*0.21));
		}
		
		if(rng.nextBoolean()) {
			double vat = rng.nextDouble()*25000;
			this.bodyData.put("zakl_dan2", String.format("%.2f", vat));
			this.bodyData.put("dan2", String.format("%.2f", vat*0.15));
		}
		
		if(rng.nextBoolean()) {
			double vat = rng.nextDouble()*25000;
			this.bodyData.put("zakl_dan3", String.format("%.2f", vat));
			this.bodyData.put("dan3", String.format("%.2f", vat*0.10));
		}
		
		if(rng.nextBoolean()) {
			this.bodyData.put("cest_sluz", String.format("%.2f", rng.nextDouble()*25000));
		}
		
		if(rng.nextBoolean()) {
			this.bodyData.put("pouzit_zboz1", String.format("%.2f", rng.nextDouble()*25000));
		}
		if(rng.nextBoolean()) {
			this.bodyData.put("pouzit_zboz2", String.format("%.2f", rng.nextDouble()*25000));
		}
		if(rng.nextBoolean()) {
			this.bodyData.put("pouzit_zboz3", String.format("%.2f", rng.nextDouble()*25000));
		}
		
		if(rng.nextBoolean()) {
			this.bodyData.put("urceno_cerp_zust", String.format("%.2f", rng.nextDouble()*25000));
		}
		if(rng.nextBoolean()) {
			this.bodyData.put("cerp_zust", String.format("%.2f", rng.nextDouble()*25000));
		}
		

		this.bodyData.put("rezim", rng.nextInt(2)+"");
		
		
		// create fake pkp and bkp signatures
		
		String hash_data = this.bodyData.get("dic_popl")+"|"
					+this.bodyData.get("id_provoz")+"|"
					+this.bodyData.get("id_pokl")+"|"
					+this.bodyData.get("porad_cis")+"|"
					+this.bodyData.get("dat_trzby")+"|"
					+this.bodyData.get("celk_trzba");
		
		String hash = null;
		
		try {
			MessageDigest hashDig = MessageDigest.getInstance("SHA-256");
			
			hashDig.update(hash_data.getBytes("UTF-8"));
			
			hash = String.format("%064x", new BigInteger(1, hashDig.digest()));
		} catch (NoSuchAlgorithmException|UnsupportedEncodingException e) {
			// fallback for hash, sha256("eet")
			hash = "7DABE8877E02BA23B8E712A5F071D7DAD444B599B7B3C410A574132D06932477";
		}
		
		// generate fake base64-encoded sha256 digest using hash - 256 bytes -> 344 chars in base64
		String pkp = this.generateCharacterString(rng, 344, hash);
		
		this.signaturesData.put("pkp", pkp);
		this.signaturesData.put("bkp", this.generateFakeSha1(rng));
	}
	
	private String generateIntegerString(Random rng, int length) {
		String ret = "";
		
		for(int a = 0; a < length; a++) {
			ret = ret.concat(rng.nextInt(10)+"");
		}
		
		return ret;
	}
	
	private String generateCharacterString(Random rng, int length, String input) {
		String ret = "";
		String[] chars = input.split("(?!^)");		
		
		for(int a = 0; a < length; a++) {
			ret = ret.concat(chars[rng.nextInt(chars.length)]);
		}
		
		return ret;
	}
	
	private String generateFakeSha1(Random rng) {
		String ret = "";
		String[] chars = "0123456789abcdefABCDEF".split("(?!^)");
		
		for(int a = 0; a < 5; a++) {
			if(a != 0) {
				ret = ret.concat("-");
			}
			for(int b = 0; b < 8; b++) {
				ret = ret.concat(chars[rng.nextInt(chars.length)]);
			}
		}
		
		
		return ret;
	}
}
