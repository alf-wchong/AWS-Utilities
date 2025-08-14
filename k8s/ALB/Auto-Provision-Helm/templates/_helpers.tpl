{{- define "cerebro.name" -}}
cerebro
{{- end }}

{{- define "cerebro.fullname" -}}
{{ include "cerebro.name" . }}-{{ .Release.Name }}
{{- end }}

{{- define "cerebro.labels" -}}
app.kubernetes.io/name: {{ include "cerebro.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "cerebro.selectorLabels" -}}
app.kubernetes.io/name: {{ include "cerebro.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
