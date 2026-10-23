package com.example.tutorapp.network

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.tutorapp.repository.safeCall
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class RazorpayManager(
    private val activity: Activity,
    private val apiService: ApiService,
    private val coroutineScope: CoroutineScope
) {

    private val TAG = "RazorpayManager"
    private val context: Context = activity

    private var currentCallback: PaymentCallback? = null

    interface PaymentCallback {
        fun onSuccess(paymentId: String, orderId: String, signature: String) //  signature add kiya
        fun onFailure(errorCode: String, errorMessage: String)
    }

    fun initCheckout() {
        Checkout.preload(activity.applicationContext)
    }

    // ── Normal payment (class/session booking) ────────────────────────────────
    fun startPayment(
        amount: Double,
        teacherId: String? = null,
        classId: String? = null,
        bookingId: String? = null,
        callback: PaymentCallback
    ) {
        currentCallback = callback
        coroutineScope.launch {
            createOrderOnBackend(amount, teacherId, classId, bookingId, callback)
        }
    }

    // ── Wallet Top-Up (uses wallet/create-topup-order endpoint) ──────────────
    fun startWalletTopUp(
        amount: Double,
        callback: PaymentCallback
    ) {
        currentCallback = callback

        coroutineScope.launch {
            try {
                val token = TokenManager.getBearerToken(context)
                val receipt = "receipt_${System.currentTimeMillis()}"

                val request = CreateOrderRequest(
                    amount = amount,
                    currency = "INR",
                    receipt = receipt
                )

                Log.d(TAG, "Creating wallet topup order... ₹$amount")

                val result = safeCall {
                    apiService.createWalletTopUpOrder(token, request)
                }

                result.fold(
                    onSuccess = { response ->
                        if (response.success == true && response.data != null) {
                            val orderId     = response.data.orderId ?: ""
                            val keyId       = response.data.keyId ?: ""
                            val amountPaise = response.data.amount ?: (amount * 100).toInt()

                            Log.d(TAG, "Wallet topup order created: $orderId")

                            activity.runOnUiThread {
                                openRazorpayCheckout(orderId, keyId, amountPaise, callback)
                            }
                        } else {
                            callback.onFailure("ORDER_FAILED", "Topup order creation failed")
                        }
                    },
                    onFailure = {
                        callback.onFailure("NETWORK_ERROR", it.message ?: "Error")
                    }
                )
            } catch (e: Exception) {
                callback.onFailure("EXCEPTION", e.message ?: "Error")
            }
        }
    }

    private fun createOrderOnBackend(
        amount: Double,
        teacherId: String?,
        classId: String?,
        bookingId: String?,
        callback: PaymentCallback
    ) {
        try {
            val token   = TokenManager.getBearerToken(context)
            val receipt = "receipt_${System.currentTimeMillis()}"

            val request = CreateOrderRequest(
                amount    = amount,
                currency  = "INR",
                receipt   = receipt,
                classId   = classId,
                sessionId = null,
                bookingId = bookingId,
                teacherId = teacherId
            )

            Log.d(TAG, " Creating order... ₹$amount")

            coroutineScope.launch {
                val result = safeCall {
                    apiService.createPaymentOrder(token, request)
                }

                result.fold(
                    onSuccess = { response ->
                        if (response.success == true && response.data != null) {
                            val orderId     = response.data.orderId ?: ""
                            val keyId       = response.data.keyId ?: "rzp_test_SdiScR9yQT87HR"
                            val amountPaise = response.data.amount ?: (amount * 100).toInt()

                            Log.d(TAG, " Order created: $orderId")

                            activity.runOnUiThread {
                                openRazorpayCheckout(orderId, keyId, amountPaise, callback)
                            }
                        } else {
                            callback.onFailure("ORDER_FAILED", "Order creation failed")
                        }
                    },
                    onFailure = {
                        callback.onFailure("NETWORK_ERROR", it.message ?: "Error")
                    }
                )
            }
        } catch (e: Exception) {
            callback.onFailure("EXCEPTION", e.message ?: "Error")
        }
    }

    private fun openRazorpayCheckout(
        orderId: String,
        keyId: String,
        amount: Int,
        callback: PaymentCallback
    ) {
        try {
            val checkout = Checkout()
            checkout.setKeyID(keyId)

            val options = JSONObject().apply {
                put("name", "EduVibe")
                put("description", "Session Fee")
                put("order_id", orderId)
                put("currency", "INR")

                val prefill = JSONObject().apply {
                    put("email", TokenManager.getUserName(context))
                    put("contact", "")
                }
                put("prefill", prefill)
            }

            Log.d(TAG, " Opening Razorpay UI...")
            checkout.open(activity, options)

        } catch (e: Exception) {
            Log.e(TAG, " Razorpay error: ${e.message}")
            callback.onFailure("EXCEPTION", e.message ?: "Payment failed")
        }
    }

    fun handlePaymentResult(
        paymentId: String?,
        data: PaymentData?,
        error: Pair<Int, String>?
    ) {
        val callback = currentCallback ?: return

        if (paymentId != null && data != null) {
            val orderId   = data.orderId   ?: ""
            val signature = data.signature ?: "" //  signature capture
            Log.d(TAG, "Payment success — paymentId=$paymentId orderId=$orderId signature=$signature")
            callback.onSuccess(paymentId, orderId, signature)
        } else if (error != null) {
            callback.onFailure(error.first.toString(), error.second)
        } else {
            callback.onFailure("UNKNOWN", "Payment failed")
        }

        currentCallback = null
    }
}