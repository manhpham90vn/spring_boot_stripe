{{- define "service.labels" -}}
app.kubernetes.io/name: {{ .Values.name }}
app.kubernetes.io/part-of: ticketing
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "service.selector" -}}
app: {{ .Values.name }}
{{- end -}}
