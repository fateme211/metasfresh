/*
 * #%L
 * de.metas.cucumber
 * %%
 * Copyright (C) 2020 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package de.metas.cucumber.stepdefs;

import de.metas.bpartner.BPGroupId;
import de.metas.bpartner.BPartnerLocationId;
import de.metas.bpartner.service.IBPartnerDAO;
import de.metas.common.rest_api.common.JsonMetasfreshId;
import de.metas.common.util.Check;
import de.metas.common.util.CoalesceUtil;
import de.metas.common.util.EmptyUtil;
import de.metas.contracts.bpartner.process.C_BPartner_MoveToAnotherOrg;
import de.metas.cucumber.stepdefs.discountschema.M_DiscountSchema_StepDefData;
import de.metas.cucumber.stepdefs.dunning.C_Dunning_StepDefData;
import de.metas.cucumber.stepdefs.org.AD_Org_StepDefData;
import de.metas.cucumber.stepdefs.pricing.M_PricingSystem_StepDefData;
import de.metas.externalreference.ExternalIdentifier;
import de.metas.externalreference.bpartner.BPartnerExternalReferenceType;
import de.metas.externalreference.rest.v1.ExternalReferenceRestControllerService;
import de.metas.order.DeliveryRule;
import de.metas.process.AdProcessId;
import de.metas.process.IADProcessDAO;
import de.metas.process.ProcessInfo;
import de.metas.product.IProductDAO;
import de.metas.util.Services;
import de.metas.util.StringUtils;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.ad.dao.IQueryBuilder;
import org.adempiere.model.InterfaceWrapperHelper;
import org.assertj.core.api.SoftAssertions;
import org.compiere.SpringContextHolder;
import org.compiere.model.I_AD_Org;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_Dunning;
import org.compiere.model.I_C_Location;
import org.compiere.model.I_C_PaymentTerm;
import org.compiere.model.I_M_DiscountSchema;
import org.compiere.model.I_M_PricingSystem;
import org.compiere.model.I_M_Product;
import org.compiere.util.Env;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static de.metas.contracts.bpartner.process.C_BPartner_MoveToAnotherOrg_ProcessHelper.PARAM_AD_ORG_TARGET_ID;
import static de.metas.contracts.bpartner.process.C_BPartner_MoveToAnotherOrg_ProcessHelper.PARAM_DATE_ORG_CHANGE;
import static de.metas.contracts.bpartner.process.C_BPartner_MoveToAnotherOrg_ProcessHelper.PARAM_IS_SHOW_MEMBERSHIP_PARAMETER;
import static de.metas.cucumber.stepdefs.StepDefConstants.ORG_ID;
import static de.metas.cucumber.stepdefs.StepDefConstants.TABLECOLUMN_IDENTIFIER;
import static de.metas.edi.model.I_C_BPartner.COLUMNNAME_IsEdiInvoicRecipient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_AD_Language;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_C_BP_Group_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_C_BPartner_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_C_BPartner_SalesRep_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_DeliveryRule;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_InvoiceRule;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_IsAllowActionPrice;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_IsCustomer;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_IsEdiDesadvRecipient;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_IsSalesRep;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_IsVendor;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_M_PricingSystem_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_PO_DiscountSchema_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_PO_InvoiceRule;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_PO_PricingSystem_ID;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_PaymentRule;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_PaymentRulePO;
import static org.compiere.model.I_C_BPartner.COLUMNNAME_Value;
import static org.compiere.model.I_C_BPartner_Location.COLUMNNAME_C_BPartner_Location_ID;
import static org.compiere.model.I_M_Product.COLUMNNAME_M_Product_ID;
import static org.compiere.model.X_C_BPartner.DELIVERYRULE_Force;

public class C_BPartner_StepDef
{
	public static final int BP_GROUP_ID = BPGroupId.ofRepoId(1000000).getRepoId();

	private final C_BPartner_StepDefData bPartnerTable;
	private final C_BPartner_Location_StepDefData bPartnerLocationTable;
	private final M_PricingSystem_StepDefData pricingSystemTable;
	private final M_Product_StepDefData productTable;
	private final M_DiscountSchema_StepDefData discountSchemaTable;
	private final C_Dunning_StepDefData dunningTable;
	private final AD_Org_StepDefData orgTable;
	private final IBPartnerDAO bpartnerDAO = Services.get(IBPartnerDAO.class);
	private final IProductDAO productDAO = Services.get(IProductDAO.class);
	private final IQueryBL queryBL = Services.get(IQueryBL.class);
	private final IADProcessDAO adProcessDAO = Services.get(IADProcessDAO.class);

	private final ExternalReferenceRestControllerService externalReferenceRestControllerService = SpringContextHolder.instance.getBean(ExternalReferenceRestControllerService.class);

	public C_BPartner_StepDef(
			@NonNull final C_BPartner_StepDefData bPartnerTable,
			@NonNull final C_BPartner_Location_StepDefData bPartnerLocationTable,
			@NonNull final M_PricingSystem_StepDefData pricingSystemTable,
			@NonNull final M_Product_StepDefData productTable,
			@NonNull final M_DiscountSchema_StepDefData discountSchemaTable,
			@NonNull final AD_Org_StepDefData orgTable,
			@NonNull final C_Dunning_StepDefData dunningTable)
	{
		this.bPartnerTable = bPartnerTable;
		this.bPartnerLocationTable = bPartnerLocationTable;
		this.pricingSystemTable = pricingSystemTable;
		this.productTable = productTable;
		this.discountSchemaTable = discountSchemaTable;
		this.dunningTable = dunningTable;
		this.orgTable = orgTable;
	}

	@Given("metasfresh contains C_BPartners:")
	public void metasfresh_contains_c_bpartners(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			createC_BPartner(tableRow, true);
		}
	}

	@Given("metasfresh contains C_BPartners without locations:")
	public void metasfresh_contains_c_bpartners_without_locations(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			createC_BPartner(tableRow, false);
		}
	}

	@And("preexisting test data is put into tableData")
	public void store_test_data_in_table_data(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final int bpartnerId = DataTableUtil.extractIntForColumnName(tableRow, COLUMNNAME_C_BPartner_ID);
			final I_C_BPartner bPartner = bpartnerDAO.getById(bpartnerId);
			assertThat(bPartner).isNotNull();

			final String bpartnerIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_BPartner_ID + "." + TABLECOLUMN_IDENTIFIER);
			bPartnerTable.put(bpartnerIdentifier, bPartner);

			final int bpartnerLocationId = DataTableUtil.extractIntForColumnName(tableRow, COLUMNNAME_C_BPartner_Location_ID);
			final BPartnerLocationId bPartnerLocationId = BPartnerLocationId.ofRepoId(bpartnerId, bpartnerLocationId);

			final I_C_BPartner_Location bPartnerLocation = bpartnerDAO.getBPartnerLocationByIdInTrx(bPartnerLocationId);
			assertThat(bPartnerLocation).isNotNull();

			final String bpartnerLocationIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_BPartner_Location_ID + "." + TABLECOLUMN_IDENTIFIER);
			bPartnerLocationTable.put(bpartnerLocationIdentifier, bPartnerLocation);

			final int productId = DataTableUtil.extractIntForColumnName(tableRow, COLUMNNAME_M_Product_ID);

			final I_M_Product product = productDAO.getById(productId);
			assertThat(product).isNotNull();

			final String productIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_M_Product_ID + "." + TABLECOLUMN_IDENTIFIER);
			productTable.put(productIdentifier, product);
		}
	}

	@And("the following c_bpartner is changed")
	public void change_bpartner(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> dataRows = dataTable.asMaps();

		for (final Map<String, String> row : dataRows)
		{
			changeBPartner(row);
		}
	}

	@And("locate bpartner by external identifier")
	public void locate_bpartner_by_external_identifier(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			locate_bpartner_by_external_identifier(tableRow);
		}
	}

	@Given("load C_BPartner:")
	public void load_bpartner(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			load_bpartner(tableRow);
		}
	}

	@Given("update C_BPartner:")
	public void update_c_bpartner(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			updateBPartner(tableRow);
		}
	}

	private void createC_BPartner(@NonNull final Map<String, String> tableRow, final boolean addDefaultLocationIfNewBPartner)
	{
		final String bPartnerName = tableRow.get("Name");
		final String bPartnerValue = CoalesceUtil.coalesce(tableRow.get("Value"), bPartnerName);

		final Integer bpGroupId = Optional.ofNullable(DataTableUtil.extractIntegerOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_C_BP_Group_ID))
				.orElse(BP_GROUP_ID);

		final int orgId = Optional.ofNullable(DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_AD_Org_ID + "." + TABLECOLUMN_IDENTIFIER))
				.map(orgTable::get)
				.map(I_AD_Org::getAD_Org_ID)
				.orElse(StepDefConstants.ORG_ID.getRepoId());

		final de.metas.edi.model.I_C_BPartner bPartnerRecord = InterfaceWrapperHelper.create(
				CoalesceUtil.coalesceSuppliers(
						() -> bpartnerDAO.retrieveBPartnerByValue(Env.getCtx(), bPartnerValue),
						() -> InterfaceWrapperHelper.newInstance(I_C_BPartner.class)), de.metas.edi.model.I_C_BPartner.class);

		bPartnerRecord.setName(bPartnerName);
		bPartnerRecord.setValue(bPartnerValue);
		bPartnerRecord.setC_BP_Group_ID(bpGroupId);
		bPartnerRecord.setIsVendor(StringUtils.toBoolean(tableRow.get("OPT." + COLUMNNAME_IsVendor), false));
		bPartnerRecord.setIsCustomer(StringUtils.toBoolean(tableRow.get("OPT." + COLUMNNAME_IsCustomer), false));
		bPartnerRecord.setIsSalesRep(StringUtils.toBoolean(tableRow.get("OPT." + COLUMNNAME_IsSalesRep), false));
		bPartnerRecord.setAD_Org_ID(orgId);
		bPartnerRecord.setDeliveryRule(DeliveryRule.FORCE.getCode());

		final String discountSchemaIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PO_DiscountSchema_ID + "." + TABLECOLUMN_IDENTIFIER);

		if (EmptyUtil.isNotBlank(discountSchemaIdentifier))
		{
			final I_M_DiscountSchema discountSchemaRecord = discountSchemaTable.get(discountSchemaIdentifier);
			bPartnerRecord.setPO_DiscountSchema_ID(discountSchemaRecord.getM_DiscountSchema_ID());
		}

		final String invoiceRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_InvoiceRule);

		if (EmptyUtil.isNotBlank(invoiceRule))
		{
			bPartnerRecord.setInvoiceRule(invoiceRule);
		}

		final String deliveryRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_DeliveryRule);
		bPartnerRecord.setDeliveryRule(CoalesceUtil.firstNotBlank(deliveryRule, DELIVERYRULE_Force));

		final String pricingSystemIdentifier = tableRow.get(I_M_PricingSystem.COLUMNNAME_M_PricingSystem_ID + ".Identifier");
		if (EmptyUtil.isNotBlank(pricingSystemIdentifier))
		{
			final int pricingSystemId = pricingSystemTable.getOptional(pricingSystemIdentifier)
					.map(I_M_PricingSystem::getM_PricingSystem_ID)
					.orElseGet(() -> Integer.parseInt(pricingSystemIdentifier));

			bPartnerRecord.setM_PricingSystem_ID(pricingSystemId);
			bPartnerRecord.setPO_PricingSystem_ID(pricingSystemId);
		}

		final String poPricingSystemIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PO_PricingSystem_ID + "." + TABLECOLUMN_IDENTIFIER);
		if (EmptyUtil.isNotBlank(poPricingSystemIdentifier))
		{
			final int poPricingSystemId = pricingSystemTable.get(poPricingSystemIdentifier).getM_PricingSystem_ID();
			bPartnerRecord.setPO_PricingSystem_ID(poPricingSystemId);
		}

		final int paymentTermId = DataTableUtil.extractIntOrMinusOneForColumnName(tableRow, "OPT.C_PaymentTerm_ID");
		if (paymentTermId > 0)
		{
			bPartnerRecord.setC_PaymentTerm_ID(paymentTermId);
			bPartnerRecord.setPO_PaymentTerm_ID(paymentTermId);
		}

		bPartnerRecord.setAD_Language(tableRow.get("OPT." + COLUMNNAME_AD_Language));

		final String salesRepIdentifier = tableRow.get("OPT." + COLUMNNAME_C_BPartner_SalesRep_ID + "." + TABLECOLUMN_IDENTIFIER);
		if (EmptyUtil.isNotBlank(salesRepIdentifier))
		{
			final I_C_BPartner salesRep = bPartnerTable.get(salesRepIdentifier);
			assertThat(salesRep).as("Missing salesrep C_BPartner record for identifier=" + salesRepIdentifier).isNotNull();

			bPartnerRecord.setC_BPartner_SalesRep_ID(salesRep.getC_BPartner_ID());
		}

		final boolean isEdiDesadvRecipient = DataTableUtil.extractBooleanForColumnNameOr(tableRow, "OPT." + COLUMNNAME_IsEdiDesadvRecipient, false);
		final boolean isEdiInvoicRecipient = DataTableUtil.extractBooleanForColumnNameOr(tableRow, "OPT." + COLUMNNAME_IsEdiInvoicRecipient, false);

		bPartnerRecord.setIsEdiDesadvRecipient(isEdiDesadvRecipient);
		bPartnerRecord.setIsEdiInvoicRecipient(isEdiInvoicRecipient);

		final String companyName = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_CompanyName);
		if (EmptyUtil.isNotBlank(companyName))
		{
			bPartnerRecord.setCompanyName(companyName);
		}

		final String adLanguage = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_AD_Language);
		if (EmptyUtil.isNotBlank(adLanguage))
		{
			bPartnerRecord.setAD_Language(adLanguage);
		}

		final String paymentTermValue = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_C_PaymentTerm_ID + ".Value");
		if (Check.isNotBlank(paymentTermValue))
		{
			final I_C_PaymentTerm paymentTerm = queryBL.createQueryBuilder(I_C_PaymentTerm.class)
					.addEqualsFilter(I_C_PaymentTerm.COLUMNNAME_Value, paymentTermValue)
					.create()
					.firstOnlyNotNull(I_C_PaymentTerm.class);

			bPartnerRecord.setC_PaymentTerm_ID(paymentTerm.getC_PaymentTerm_ID());
			bPartnerRecord.setPO_PaymentTerm_ID(paymentTerm.getC_PaymentTerm_ID());
		}

		final String paymentRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PaymentRule);
		if (Check.isNotBlank(paymentRule))
		{
			bPartnerRecord.setPaymentRule(paymentRule);
		}

		final String paymentRulePO = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PaymentRulePO);
		if (Check.isNotBlank(paymentRulePO))
		{
			bPartnerRecord.setPaymentRulePO(paymentRulePO);
		}

		final String poInvoiceRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PO_InvoiceRule);
		if (EmptyUtil.isNotBlank(poInvoiceRule))
		{
			bPartnerRecord.setPO_InvoiceRule(poInvoiceRule);
		}

		final Boolean allowCampaignPrice = DataTableUtil.extractBooleanForColumnNameOr(tableRow, "OPT." + COLUMNNAME_IsAllowActionPrice, null);
		if (allowCampaignPrice != null)
		{
			bPartnerRecord.setIsAllowActionPrice(allowCampaignPrice);
		}

		final String bpOrgIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_AD_OrgBP_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
		if (Check.isNotBlank(bpOrgIdentifier))
		{
			final I_AD_Org org = orgTable.get(bpOrgIdentifier);
			bPartnerRecord.setAD_OrgBP_ID(org.getAD_Org_ID());
		}

		final boolean alsoCreateLocation = InterfaceWrapperHelper.isNew(bPartnerRecord) && addDefaultLocationIfNewBPartner;
		InterfaceWrapperHelper.saveRecord(bPartnerRecord);

		if (alsoCreateLocation)
		{
			final I_C_Location locationRecord = InterfaceWrapperHelper.newInstance(I_C_Location.class);
			locationRecord.setC_Country_ID(StepDefConstants.COUNTRY_ID.getRepoId());
			InterfaceWrapperHelper.saveRecord(locationRecord);

			final I_C_BPartner_Location bPartnerLocationRecord = InterfaceWrapperHelper.newInstance(I_C_BPartner_Location.class);
			bPartnerLocationRecord.setC_BPartner_ID(bPartnerRecord.getC_BPartner_ID());
			bPartnerLocationRecord.setC_Location_ID(locationRecord.getC_Location_ID());
			bPartnerLocationRecord.setIsBillToDefault(true);
			bPartnerLocationRecord.setIsShipTo(true);

			final String gln = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner_Location.COLUMNNAME_GLN);
			if (EmptyUtil.isNotBlank(gln))
			{
				bPartnerLocationRecord.setGLN(gln);
			}

			InterfaceWrapperHelper.saveRecord(bPartnerLocationRecord);

			final String locationIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_C_BPartner_Location_ID + "." + TABLECOLUMN_IDENTIFIER);
			if (EmptyUtil.isNotBlank(locationIdentifier))
			{
				bPartnerLocationTable.put(locationIdentifier, bPartnerLocationRecord);
			}
		}

		final String recordIdentifier = DataTableUtil.extractRecordIdentifier(tableRow, "C_BPartner");
		bPartnerTable.putOrReplace(recordIdentifier, bPartnerRecord);
	}

	private void changeBPartner(@NonNull final Map<String, String> row)
	{
		final String bPartnerIdentifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_BPartner_ID + ".Identifier");

		final Integer bPartnerId = bPartnerTable.getOptional(bPartnerIdentifier)
				.map(I_C_BPartner::getC_BPartner_ID)
				.orElseGet(() -> Integer.parseInt(bPartnerIdentifier));

		final de.metas.edi.model.I_C_BPartner bPartnerRecord = InterfaceWrapperHelper.load(bPartnerId, de.metas.edi.model.I_C_BPartner.class);

		final String name2 = DataTableUtil.extractNullableStringForColumnName(row, "OPT." + I_C_BPartner.COLUMNNAME_Name2);

		if (Check.isNotBlank(name2))
		{
			bPartnerRecord.setName2(DataTableUtil.nullToken2Null(name2));
		}

		final String vaTaxId = DataTableUtil.extractNullableStringForColumnName(row, "OPT." + I_C_BPartner.COLUMNNAME_VATaxID);

		if (Check.isNotBlank(vaTaxId))
		{
			bPartnerRecord.setVATaxID(DataTableUtil.nullToken2Null(vaTaxId));
		}

		final boolean isDesadvRecipient = DataTableUtil.extractBooleanForColumnNameOr(row, "OPT." + de.metas.edi.model.I_C_BPartner.COLUMNNAME_IsEdiDesadvRecipient, false);
		bPartnerRecord.setIsEdiDesadvRecipient(isDesadvRecipient);

		final String ediDesadvRecipientGLN = DataTableUtil.extractNullableStringForColumnName(row, "OPT." + de.metas.edi.model.I_C_BPartner.COLUMNNAME_EdiDesadvRecipientGLN);

		if (Check.isNotBlank(ediDesadvRecipientGLN))
		{
			bPartnerRecord.setEdiDesadvRecipientGLN(DataTableUtil.nullToken2Null(ediDesadvRecipientGLN));
		}

		final String ediInvoicRecipientGLN = DataTableUtil.extractNullableStringForColumnName(row, "OPT." + de.metas.edi.model.I_C_BPartner.COLUMNNAME_EdiInvoicRecipientGLN);

		if (Check.isNotBlank(ediInvoicRecipientGLN))
		{
			bPartnerRecord.setEdiInvoicRecipientGLN(DataTableUtil.nullToken2Null(ediInvoicRecipientGLN));
		}

		final boolean isInvoicRecipient = DataTableUtil.extractBooleanForColumnNameOr(row, "OPT." + de.metas.edi.model.I_C_BPartner.COLUMNNAME_IsEdiInvoicRecipient, false);
		bPartnerRecord.setIsEdiInvoicRecipient(isInvoicRecipient);

		final String deliveryRule = DataTableUtil.extractNullableStringForColumnName(row, "OPT." + de.metas.edi.model.I_C_BPartner.COLUMNNAME_DeliveryRule);

		if (Check.isNotBlank(deliveryRule))
		{
			bPartnerRecord.setDeliveryRule(DataTableUtil.nullToken2Null(deliveryRule));
		}

		InterfaceWrapperHelper.save(bPartnerRecord);
	}

	private void locate_bpartner_by_external_identifier(@NonNull final Map<String, String> row)
	{
		final ExternalIdentifier externalIdentifier = ExternalIdentifier.of(DataTableUtil.extractStringForColumnName(row, "externalIdentifier"));

		final Optional<JsonMetasfreshId> bpartnerIdOptional = externalReferenceRestControllerService.getJsonMetasfreshIdFromExternalReference(ORG_ID, externalIdentifier, BPartnerExternalReferenceType.BPARTNER);
		assertThat(bpartnerIdOptional).isPresent();

		final I_C_BPartner bPartnerRecord = bpartnerDAO.getById(bpartnerIdOptional.get().getValue());
		assertThat(bPartnerRecord).isNotNull();

		final String bpartnerIdentifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_BPartner_ID + "." + TABLECOLUMN_IDENTIFIER);
		bPartnerTable.putOrReplace(bpartnerIdentifier, bPartnerRecord);
	}

	private void load_bpartner(@NonNull final Map<String, String> row)
	{
		final String identifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_BPartner_ID + ".Identifier");

		final Integer id = DataTableUtil.extractIntegerOrNullForColumnName(row, "OPT." + COLUMNNAME_C_BPartner_ID);

		if (id != null)
		{
			final I_C_BPartner bPartnerRecord = bpartnerDAO.getById(id);
			assertThat(bPartnerRecord).isNotNull();

			bPartnerTable.putOrReplace(identifier, bPartnerRecord);
		}

		final String value = DataTableUtil.extractStringOrNullForColumnName(row, "OPT." + COLUMNNAME_Value);

		if (Check.isNotBlank(value))
		{
			final I_C_BPartner bPartnerRecord = bpartnerDAO.retrieveBPartnerByValue(Env.getCtx(), value);
			assertThat(bPartnerRecord).isNotNull();

			bPartnerTable.putOrReplace(identifier, bPartnerRecord);
		}
	}

	private void updateBPartner(@NonNull final Map<String, String> tableRow)
	{
		final String bPartnerIdentifier = DataTableUtil.extractRecordIdentifier(tableRow, "C_BPartner");

		final I_C_BPartner bPartner = bPartnerTable.get(bPartnerIdentifier);

		assertThat(bPartner).isNotNull();

		final String invoiceRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_InvoiceRule);
		if (EmptyUtil.isNotBlank(invoiceRule))
		{
			bPartner.setInvoiceRule(invoiceRule);
		}

		final String poInvoiceRule = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_PO_InvoiceRule);
		if (EmptyUtil.isNotBlank(poInvoiceRule))
		{
			bPartner.setPO_InvoiceRule(poInvoiceRule);
		}

		final String dunningIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_C_Dunning_ID + "." + TABLECOLUMN_IDENTIFIER);
		if (EmptyUtil.isNotBlank(dunningIdentifier))
		{
			final I_C_Dunning dunning = dunningTable.get(dunningIdentifier);
			bPartner.setC_Dunning_ID(dunning.getC_Dunning_ID());
		}

		final String pricingSystemIdentifier = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + COLUMNNAME_M_PricingSystem_ID + "." + TABLECOLUMN_IDENTIFIER);
		if (EmptyUtil.isNotBlank(pricingSystemIdentifier))
		{
			final I_M_PricingSystem pricingSystem = pricingSystemTable.get(pricingSystemIdentifier);
			bPartner.setM_PricingSystem_ID(pricingSystem.getM_PricingSystem_ID());
		}

		InterfaceWrapperHelper.save(bPartner);

		bPartnerTable.putOrReplace(bPartnerIdentifier, bPartner);
	}

	@And("validate C_BPartner:")
	public void validate_C_BPartner(@NonNull final DataTable dataTable)
	{
		final SoftAssertions softly = new SoftAssertions();

		for (final Map<String, String> row : dataTable.asMaps())
		{
			final String bpIdentifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_BPartner_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final I_C_BPartner bPartnerRecord = bPartnerTable.get(bpIdentifier);

			final String bpValue = DataTableUtil.extractStringForColumnName(row, I_C_BPartner.COLUMNNAME_Value);
			softly.assertThat(bPartnerRecord.getValue()).as("Value").isEqualTo(bpValue);

			final String companyName = DataTableUtil.extractStringOrNullForColumnName(row, "OPT." + I_C_BPartner.COLUMNNAME_CompanyName);
			if (Check.isNotBlank(companyName))
			{
				softly.assertThat(bPartnerRecord.getCompanyName()).as("CompanyName").isEqualTo(companyName);
			}

			final String vaTaxID = DataTableUtil.extractStringOrNullForColumnName(row, "OPT." + I_C_BPartner.COLUMNNAME_VATaxID);
			if (Check.isNotBlank(vaTaxID))
			{
				softly.assertThat(bPartnerRecord.getVATaxID()).as("VATaxID").isEqualTo(vaTaxID);
			}

			final Boolean isManuallyCreated = DataTableUtil.extractBooleanForColumnNameOr(row, "OPT." + I_C_BPartner.COLUMNNAME_IsManuallyCreated, false);
			softly.assertThat(bPartnerRecord.isManuallyCreated()).as("IsManuallyCreated").isEqualTo(isManuallyCreated);
		}

		softly.assertAll();
	}

	@And("^after not more than (.*)s, C_BPartner are found:$")
	public void validate_created_c_bPartners(final int timeoutSec, @NonNull final DataTable table) throws InterruptedException
	{
		final List<Map<String, String>> dataTable = table.asMaps();
		for (final Map<String, String> tableRow : dataTable)
		{
			final String bPartnerIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_BPartner_ID + "." + TABLECOLUMN_IDENTIFIER);
			final IQueryBuilder<I_C_BPartner> query = queryBL.createQueryBuilder(I_C_BPartner.class);

			final String bPartnerName = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_Name);
			if(!Check.isEmpty(bPartnerName))
			{
				query.addEqualsFilter(I_C_BPartner.COLUMNNAME_Name, bPartnerName);
			}

			final String bPartnerValue = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_Value);
			if(!Check.isEmpty(bPartnerValue))
			{
				query.addEqualsFilter(I_C_BPartner.COLUMNNAME_Value, bPartnerValue);
			}

			final int orgId = Optional.ofNullable(DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_BPartner.COLUMNNAME_AD_Org_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER))
					.map(orgTable::get)
					.map(I_AD_Org::getAD_Org_ID)
					.orElse(StepDefConstants.ORG_ID.getRepoId());
			query.addEqualsFilter(I_C_BPartner.COLUMNNAME_AD_Org_ID, orgId);

			final Supplier<Boolean> QueryExecutor = () -> {
				final I_C_BPartner bPartnerRecord = query.create().firstOnly(I_C_BPartner.class);

				if (bPartnerRecord == null)
				{
					return false;
				}

				bPartnerTable.putOrReplace(bPartnerIdentifier, bPartnerRecord);
				return true;
			};

			StepDefUtil.tryAndWait(timeoutSec, 500, QueryExecutor);
		}
	}


	@Given("C_BPartner_MoveToAnotherOrg is invoked with parameters:")
	public void C_BPartner_MoveToAnotherOrg(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final String bPartnerIdentifier = DataTableUtil.extractStringForColumnName(tableRow, I_C_BPartner.COLUMNNAME_C_BPartner_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final I_C_BPartner bPartnerRecord = bPartnerTable.get(bPartnerIdentifier);

			final String orgIdentifier = DataTableUtil.extractStringForColumnName(tableRow, I_AD_Org.COLUMNNAME_AD_Org_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final I_AD_Org orgRecord = orgTable.get(orgIdentifier);

			final Timestamp changeDate = DataTableUtil.extractDateTimestampForColumnName(tableRow, PARAM_DATE_ORG_CHANGE);

			final AdProcessId processId = adProcessDAO.retrieveProcessIdByClass(C_BPartner_MoveToAnotherOrg.class);

			final ProcessInfo.ProcessInfoBuilder processInfoBuilder = ProcessInfo.builder();
			processInfoBuilder.setAD_Process_ID(processId.getRepoId());
			processInfoBuilder.setRecord(I_C_BPartner.Table_Name, bPartnerRecord.getC_BPartner_ID());
			processInfoBuilder.addParameter(PARAM_AD_ORG_TARGET_ID, orgRecord.getAD_Org_ID());
			processInfoBuilder.addParameter(PARAM_DATE_ORG_CHANGE, changeDate);
			processInfoBuilder.addParameter(PARAM_IS_SHOW_MEMBERSHIP_PARAMETER, false);

			processInfoBuilder
					.buildAndPrepareExecution()
					.executeSync()
					.getResult();
		}

	}
}
