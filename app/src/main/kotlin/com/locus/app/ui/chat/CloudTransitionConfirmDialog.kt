package com.locus.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.locus.core.domain.routing.RouteDecision

/**
 * SEC-5 transition warning dialog: "The app must show an explicit warning identifying the cloud
 * provider and requiring user confirmation before any note content is transmitted. The passive
 * SEC-2 active-model indicator is necessary but not sufficient."
 */
@Suppress("LongParameterList")
@Composable
fun CloudTransitionConfirmDialog(
    providerId: String,
    fromModelName: String,
    toModelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cloud Transition Warning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Switching from local model '$fromModelName' to cloud model " +
                        "'$toModelName' provided by $providerId.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "The app must show an explicit warning identifying the cloud provider " +
                        "and requiring user confirmation before any note content is transmitted. " +
                        "The passive SEC-2 active-model indicator is necessary but not sufficient.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Do you want to send your note content to $providerId?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Confirm & Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = modifier,
    )
}

/** Overload accepting [RouteDecision.RequiresCloudTransitionConfirmation] directly. */
@Composable
fun CloudTransitionConfirmDialog(
    decision: RouteDecision.RequiresCloudTransitionConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CloudTransitionConfirmDialog(
        providerId = decision.providerId,
        fromModelName = decision.fromModel.id,
        toModelName = decision.toModel.id,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
