{{/*
Refusals in one place, so a missing value stops the render rather than the pod.
*/}}
{{- define "haul.require" -}}
{{- if not .value }}{{ fail (printf "haul: %s is required — %s" .name .because) }}{{ end }}
{{- .value }}
{{- end }}

{{- define "haul.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/part-of: haul
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "haul.image" -}}
{{ .Values.server.image }}:{{ include "haul.require" (dict "name" "server.version" "because" "a tag that moves leaves helm nothing to notice, so a green deploy keeps running the previous binary" "value" .Values.server.version) }}
{{- end }}

{{/*
The database address, spelled once: the server reads it, and the probe of the database is its Service.
*/}}
{{- define "haul.dbUrl" -}}
jdbc:postgresql://{{ .Release.Name }}-postgres:5432/{{ .Values.postgres.database }}
{{- end }}

{{/*
An agent's pair of variables, or neither. `endpoint` without `key` is refused here, by a message that
names a value somebody can edit, before the server refuses it in a pod that is already failing.
*/}}
{{- define "haul.agentEnv" -}}
{{- $agent := .agent }}
{{- if and $agent.endpoint (not $agent.key) }}
{{- fail (printf "haul: observability.%s.endpoint is set and observability.%s.key is empty — the server refuses to start on one without the other" .name .name) }}
{{- end }}
{{- if and $agent.key (not $agent.endpoint) }}
{{- fail (printf "haul: observability.%s.key is set and observability.%s.endpoint is empty — a key with nowhere to send observes nothing" .name .name) }}
{{- end }}
{{- if $agent.endpoint }}
- name: HAUL_{{ .name | upper }}_ENDPOINT
  value: {{ $agent.endpoint | quote }}
- name: HAUL_{{ .name | upper }}_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .root.Release.Name }}-secrets
      key: {{ .name }}-key
{{- end }}
{{- end }}

{{/*
The provider's address (`SHILDIK_ISSUER`): what it writes into every token and serves in discovery.
Behind the ingress it is the public host and nothing else — the browser and the server must reach the
same name — so it is spelled from `shildik.hostname` unless a cluster without an ingress names its own.
*/}}
{{- define "haul.shildikIssuer" -}}
{{- if .Values.shildik.issuer -}}
{{- .Values.shildik.issuer | trimSuffix "/" -}}
{{- else -}}
https://{{ include "haul.require" (dict "name" "shildik.hostname" "because" "the browser opens the provider's page there and the server reads the realm's keys from it" "value" .Values.shildik.hostname) }}
{{- end -}}
{{- end }}

{{/* The realm's issuer, `{base}/realms/{realm}` — what the server is configured with (`HAUL_OIDC_ISSUER`). */}}
{{- define "haul.realmIssuer" -}}
{{ include "haul.shildikIssuer" . }}/realms/{{ .Values.shildik.realm }}
{{- end }}

{{/*
The storefront's one return page, registered on its public client: `signed-in.html` beside the bundle,
on the storefront's own host (the client builds `<origin>/signed-in.html`, `Main.kt`).
*/}}
{{- define "haul.redirectUri" -}}
https://{{ include "haul.require" (dict "name" "hostname" "because" "the storefront's public client registers https://<hostname>/signed-in.html as its return page" "value" .Values.hostname) }}/signed-in.html
{{- end }}

{{/*
The key that opens the test card ···· 0002 — the one the payment simulator always declines — to the synthetic
shoppers (B-89; the server's `HAUL_TEST_CARD_KEY`). Derived from the provider's master keys, a secret the release
already holds and keeps across upgrades, so it is stable, is never written in a values file, and is nothing new
for a deploy to pass; a one-way hash, so it gives the master keys away to nobody. Empty unless the shoppers run
and some of their walks decline: then the server has no key, and the test card is nobody's.
*/}}
{{- define "haul.testCardKey" -}}
{{- $shoppers := .Values.shoppers }}
{{- if and $shoppers.enabled (regexMatch "^[1-9][0-9]*$" (toString $shoppers.declineEvery)) -}}
{{- printf "haul-test-card-0002:%s" (include "haul.require" (dict "name" "shildik.masterKeys" "because" "the test card's key is derived from it" "value" .Values.shildik.masterKeys)) | sha256sum -}}
{{- end -}}
{{- end }}

{{/*
Whether the server makes demo shoppers (B-162): `server.demoShoppers` with the release's own provider, whose
management port and bootstrap token it uses. "true" or empty.
*/}}
{{- define "haul.demoShoppers" -}}
{{- if and .Values.shildik.enabled .Values.server.demoShoppers -}}true{{- end -}}
{{- end }}
