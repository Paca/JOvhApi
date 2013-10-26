package be.nicolaspirlot.ovhrestapi.bill;

import java.util.Date;

public class Bill 
{
	public String pdfUrl;
	public Date date;
	public Price priceWithoutTax;
	public Price tax;
	public String billId;
	public String password;
	public double orderId;
	public String url;
	public Price priceWithTax;
}
