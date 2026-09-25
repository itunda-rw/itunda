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
