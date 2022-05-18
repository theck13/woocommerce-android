package com.woocommerce.android.ui.coupons.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import com.woocommerce.android.R
import com.woocommerce.android.model.Coupon
import com.woocommerce.android.model.Coupon.Type
import com.woocommerce.android.model.Coupon.Type.Percent
import com.woocommerce.android.ui.compose.component.BigDecimalTextFieldValueMapper
import com.woocommerce.android.ui.compose.component.DatePickerDialog
import com.woocommerce.android.ui.compose.component.WCColoredButton
import com.woocommerce.android.ui.compose.component.WCOutlinedButton
import com.woocommerce.android.ui.compose.component.WCOutlinedSpinner
import com.woocommerce.android.ui.compose.component.WCOutlinedTextField
import com.woocommerce.android.ui.compose.component.WCOutlinedTypedTextField
import com.woocommerce.android.ui.compose.component.WCSwitch
import com.woocommerce.android.ui.compose.component.WCTextButton
import com.woocommerce.android.ui.compose.theme.WooTheme
import com.woocommerce.android.ui.coupons.edit.EditCouponViewModel.ViewState
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun EditCouponScreen(viewModel: EditCouponViewModel) {
    viewModel.viewState.observeAsState().value?.let {
        EditCouponScreen(
            viewState = it,
            onAmountChanged = viewModel::onAmountChanged,
            onCouponCodeChanged = viewModel::onCouponCodeChanged,
            onRegenerateCodeClick = viewModel::onRegenerateCodeClick,
            onDescriptionButtonClick = viewModel::onDescriptionButtonClick,
            onExpiryDateChanged = viewModel::onExpiryDateChanged,
            onFreeShippingChanged = viewModel::onFreeShippingChanged
        )
    }
}

@Composable
fun EditCouponScreen(
    viewState: EditCouponViewModel.ViewState,
    onAmountChanged: (BigDecimal?) -> Unit = {},
    onCouponCodeChanged: (String) -> Unit = {},
    onRegenerateCodeClick: () -> Unit = {},
    onDescriptionButtonClick: () -> Unit = {},
    onExpiryDateChanged: (Date?) -> Unit = {},
    onFreeShippingChanged: (Boolean) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
        modifier = Modifier
            .background(color = MaterialTheme.colors.surface)
            .verticalScroll(scrollState)
            .padding(
                horizontal = dimensionResource(id = R.dimen.major_100),
                vertical = dimensionResource(id = R.dimen.major_100)
            )
            .fillMaxSize()
    ) {
        DetailsSection(
            viewState = viewState,
            onAmountChanged = onAmountChanged,
            onCouponCodeChanged = onCouponCodeChanged,
            onRegenerateCodeClick = onRegenerateCodeClick,
            onDescriptionButtonClick = onDescriptionButtonClick,
            onExpiryDateChanged = onExpiryDateChanged,
            onFreeShippingChanged = onFreeShippingChanged
        )
        ConditionsSection(viewState)
        UsageRestrictionsSection(viewState)
        WCColoredButton(
            onClick = { /*TODO*/ },
            text = stringResource(id = R.string.coupon_edit_save_button),
            modifier = Modifier.fillMaxWidth(),
            enabled = viewState.hasChanges
        )
    }
}

@Composable
private fun DetailsSection(
    viewState: ViewState,
    onAmountChanged: (BigDecimal?) -> Unit,
    onCouponCodeChanged: (String) -> Unit,
    onRegenerateCodeClick: () -> Unit,
    onDescriptionButtonClick: () -> Unit,
    onExpiryDateChanged: (Date?) -> Unit,
    onFreeShippingChanged: (Boolean) -> Unit
) {
    val couponDraft = viewState.couponDraft
    val focusManager = LocalFocusManager.current
    Column(
        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Text(
            text = stringResource(id = R.string.coupon_edit_details_section).toUpperCase(Locale.current),
            style = MaterialTheme.typography.body2,
            color = colorResource(id = R.color.color_on_surface_medium)
        )
        AmountField(viewState.couponDraft.amount, viewState.amountUnit, viewState.couponDraft.type, onAmountChanged)
        // Coupon code field: display code uppercased, but always return it lowercased
        WCOutlinedTextField(
            value = couponDraft.code.orEmpty().toUpperCase(Locale.current),
            label = stringResource(id = R.string.coupon_edit_code_hint),
            onValueChange = { onCouponCodeChanged(it.toLowerCase(Locale.current)) },
            helperText = stringResource(id = R.string.coupon_edit_code_helper),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        WCTextButton(
            onClick = {
                focusManager.clearFocus()
                onRegenerateCodeClick()
            },
            text = stringResource(id = R.string.coupon_edit_regenerate_coupon)
        )
        DescriptionButton(viewState.couponDraft.description, onDescriptionButtonClick)
        ExpiryField(viewState.couponDraft.dateExpires, onExpiryDateChanged)
        WCSwitch(
            text = stringResource(id = R.string.coupon_edit_free_shipping),
            checked = viewState.couponDraft.isShippingFree ?: false,
            onCheckedChange = onFreeShippingChanged,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
@Suppress("UnusedPrivateMember")
private fun ConditionsSection(viewState: EditCouponViewModel.ViewState) {
    /*TODO*/
}

@Composable
@Suppress("UnusedPrivateMember")
private fun UsageRestrictionsSection(viewState: EditCouponViewModel.ViewState) {
    /*TODO*/
}

@Composable
private fun AmountField(amount: BigDecimal?, amountUnit: String, type: Type?, onAmountChanged: (BigDecimal?) -> Unit) {
    WCOutlinedTypedTextField(
        value = amount ?: BigDecimal.ZERO,
        label = stringResource(id = R.string.coupon_edit_amount_hint, amountUnit),
        valueMapper = BigDecimalTextFieldValueMapper(supportsNegativeValue = true),
        onValueChange = onAmountChanged,
        helperText = stringResource(
            if (type is Percent) R.string.coupon_edit_amount_percentage_helper
            else R.string.coupon_edit_amount_rate_helper
        ),
        // TODO use KeyboardType.Decimal after updating to Compose 1.2.0
        //  (https://issuetracker.google.com/issues/209835363)
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DescriptionButton(description: String?, onButtonClicked: () -> Unit) {
    WCOutlinedButton(
        onClick = onButtonClicked,
        text = stringResource(
            id = if (description.isNullOrEmpty()) R.string.coupon_edit_add_description
            else R.string.coupon_edit_edit_description
        ),
        leadingIcon = {
            Icon(
                imageVector = if (description.isNullOrEmpty()) Icons.Filled.Add else Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(dimensionResource(id = R.dimen.major_100))
            )
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface),
        modifier = Modifier.fillMaxWidth()
    )
}

@Suppress("LongMethod")
@Composable
private fun ExpiryField(dateExpires: Date?, onExpiryDateChanged: (Date?) -> Unit) {
    val dateFormat = remember { SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM) }
    var showEditDateDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    WCOutlinedSpinner(
        onClick = {
            if (dateExpires != null) {
                showEditDateDialog = true
            } else {
                showDatePicker = true
            }
        },
        value = dateExpires?.let { dateFormat.format(it) }
            ?: stringResource(id = R.string.coupon_edit_expiry_date_none),
        label = stringResource(id = R.string.coupon_edit_expiry_date),
        modifier = Modifier.fillMaxWidth()
    )

    if (showEditDateDialog) {
        AlertDialog(
            onDismissRequest = { showEditDateDialog = false },
            properties = DialogProperties(),
            buttons = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.major_100)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(id = R.dimen.major_100))
                        .background(MaterialTheme.colors.surface)
                ) {
                    WCOutlinedButton(
                        text = stringResource(id = R.string.coupon_edit_expiry_date_dialog_edit),
                        onClick = {
                            showEditDateDialog = false
                            showDatePicker = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    WCOutlinedButton(
                        text = stringResource(id = R.string.coupon_edit_expiry_date_dialog_delete),
                        onClick = {
                            showEditDateDialog = false
                            onExpiryDateChanged(null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            currentDate = dateExpires,
            onDateSelected = {
                showDatePicker = false
                onExpiryDateChanged(it)
            },
            onDismissRequest = { showDatePicker = false },
            dateFormat = dateFormat
        )
    }
}

@Composable
@Preview
private fun EditCouponPreview() {
    WooTheme {
        EditCouponScreen(
            viewState = EditCouponViewModel.ViewState(
                couponDraft = Coupon(
                    id = 0L,
                    code = "code",
                    amount = BigDecimal.TEN,
                    isShippingFree = true,
                    productIds = emptyList(),
                    categoryIds = emptyList(),
                    excludedProductIds = emptyList(),
                    excludedCategoryIds = emptyList(),
                    restrictedEmails = emptyList()
                ),
                localizedType = "Fixed Rate Discount",
                amountUnit = "%",
                hasChanges = true
            )
        )
    }
}
