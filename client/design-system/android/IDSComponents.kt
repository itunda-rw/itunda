package com.itunda.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** IDS component contract: variants, states, focus and disabled behavior stay consistent with Web/iOS. */
@Composable fun IDSPrimaryButton(text:String, enabled:Boolean=true, loading:Boolean=false, onClick:()->Unit) {
    Button(onClick=onClick, enabled=enabled && !loading, shape=RoundedCornerShape(IDSRadii.md.dp), modifier=Modifier.height(48.dp)) {
        if (loading) CircularProgressIndicator(modifier=Modifier.size(18.dp), strokeWidth=2.dp) else Text(text)
    }
}

@Composable fun IDSSecondaryButton(text:String, enabled:Boolean=true, onClick:()->Unit) {
    OutlinedButton(onClick=onClick, enabled=enabled, shape=RoundedCornerShape(IDSRadii.md.dp), modifier=Modifier.height(48.dp)) { Text(text) }
}

@Composable fun IDSField(value:String, onValueChange:(String)->Unit, label:String, error:String?=null, enabled:Boolean=true) {
    OutlinedTextField(value=value,onValueChange=onValueChange,label={Text(label)},enabled=enabled,isError=error!=null,supportingText={error?.let{Text(it)}},modifier=Modifier.fillMaxWidth())
}


@Composable fun IDSAlert(title:String, message:String, onDismiss:(()->Unit)?=null) {
    Card(shape=RoundedCornerShape(IDSRadii.md.dp), modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { Text(title, style=MaterialTheme.typography.titleMedium); Text(message, style=MaterialTheme.typography.bodyMedium); onDismiss?.let { TextButton(onClick=it){Text("Dismiss")} } }
    }
}
@Composable fun IDSCard(content:@Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(IDSRadii.lg.dp), modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content=content) }
}


@Composable
fun IDSList(items: List<Pair<String, String>>, onItemClick: (Int) -> Unit = {}) {
    Card(shape = RoundedCornerShape(IDSRadii.lg.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            items.forEachIndexed { index, item ->
                TextButton(
                    onClick = { onItemClick(index) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.first, style = MaterialTheme.typography.titleSmall)
                        Text(item.second, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (index < items.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
fun IDSTabs(labels: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 0.dp) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                text = { Text(label) }
            )
        }
    }
}

@Composable
fun IDSBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.fillMaxWidth().padding(20.dp), content = content)
        }
    }
}

@Composable
fun IDSEmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            IDSPrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun IDSErrorState(title: String = "Something went wrong", message: String, onRetry: (() -> Unit)? = null) {
    IDSAlert(title, message, onRetry)
}

@Composable
fun IDSLoadingState() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
        CircularProgressIndicator()
    }
}
