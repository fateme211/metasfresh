/*
 * #%L
 * de.metas.contracts
 * %%
 * Copyright (C) 2023 metas GmbH
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

package de.metas.contracts.modular.impl;

import de.metas.contracts.FlatrateTermId;
import de.metas.contracts.IFlatrateBL;
import de.metas.contracts.flatrate.TypeConditions;
import de.metas.contracts.modular.IModularContractTypeHandler;
import de.metas.contracts.modular.ModelAction;
import de.metas.contracts.modular.ModularContractHandlerType;
import de.metas.contracts.modular.ModularContract_Constants;
import de.metas.contracts.modular.log.LogEntryContractType;
import de.metas.contracts.modular.log.ModularContractLogService;
import de.metas.inout.IInOutDAO;
import de.metas.inout.InOutId;
import de.metas.order.IOrderBL;
import de.metas.order.OrderId;
import de.metas.order.OrderLineId;
import de.metas.util.Services;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_M_InOut;
import org.compiere.model.I_M_InOutLine;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

import static de.metas.contracts.modular.ModularContractHandlerType.SHIPMENT_LINE_FOR_SO_MODULAR;
import static de.metas.contracts.modular.ModularContract_Constants.MSG_ERROR_PROCESSED_LOGS_CANNOT_BE_RECOMPUTED;

@Component
@RequiredArgsConstructor
public class ShipmentLineForSOModularContractHandler implements IModularContractTypeHandler<I_M_InOutLine>
{
	private final IInOutDAO inOutDao = Services.get(IInOutDAO.class);
	private final IFlatrateBL flatrateBL = Services.get(IFlatrateBL.class);
	private final IOrderBL orderBL = Services.get(IOrderBL.class);

	@NonNull
	private final ModularContractLogService contractLogService;

	@Override
	@NonNull
	public Class<I_M_InOutLine> getType()
	{
		return I_M_InOutLine.class;
	}

	@Override
	public boolean applies(final @NonNull I_M_InOutLine inOutLineRecord)
	{
		final I_M_InOut inOutRecord = inOutDao.getById(InOutId.ofRepoId(inOutLineRecord.getM_InOut_ID()));
		final OrderId orderId = OrderId.ofRepoIdOrNull(inOutLineRecord.getC_Order_ID());
		if (orderId == null)
		{
			return false;
		}
		return inOutRecord.isSOTrx();
	}

	@Override
	public boolean applies(final @NonNull LogEntryContractType logEntryContractType)
	{
		return logEntryContractType.isModularContractType();
	}

	@Override
	public @NonNull Stream<FlatrateTermId> streamContractIds(@NonNull final I_M_InOutLine inOutLineRecord)
	{
		final I_C_Order order = orderBL.getById(OrderId.ofRepoId(inOutLineRecord.getC_Order_ID()));

		if (!order.isSOTrx())
		{
			return Stream.empty();
		}

		return flatrateBL.getByOrderLineId(OrderLineId.ofRepoId(inOutLineRecord.getC_OrderLine_ID()), TypeConditions.MODULAR_CONTRACT)
				.map(flatrateTerm -> FlatrateTermId.ofRepoId(flatrateTerm.getC_Flatrate_Term_ID()))
				.stream();
	}

	@Override
	public void validateAction(final @NonNull I_M_InOutLine model, final @NonNull ModelAction action)
	{
		switch (action)
		{
			case VOIDED, REACTIVATED -> throw new AdempiereException(ModularContract_Constants.MSG_ERROR_DOC_ACTION_NOT_ALLOWED);
			case COMPLETED, REVERSED ->
			{
			}
			case RECREATE_LOGS -> contractLogService.throwErrorIfProcessedLogsExistForRecord(TableRecordReference.of(model),
																							 MSG_ERROR_PROCESSED_LOGS_CANNOT_BE_RECOMPUTED);
			default -> throw new AdempiereException(ModularContract_Constants.MSG_ERROR_DOC_ACTION_UNSUPPORTED);
		}
	}

	@Override
	public @NonNull ModularContractHandlerType getHandlerType()
	{
		return SHIPMENT_LINE_FOR_SO_MODULAR;
	}
}
