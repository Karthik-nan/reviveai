import { useEffect, useState } from "react";
import {
  RefreshCw,
  Database,
  Server,
  Radio,
  CreditCard,
  BrainCircuit,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
} from "lucide-react";

import { getSystemSettings } from "../api/settingsApi";

const settingIcons = {
  Application: Server,
  Database: Database,
  Redis: Database,
  Kafka: Radio,
  Razorpay: CreditCard,
  AI: BrainCircuit,
};

function Settings() {
  const [settings, setSettings] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadSettings = async () => {
    try {
      setLoading(true);
      setError("");

      const data = await getSystemSettings();

      setSettings(data);
    } catch (err) {
      console.error("Failed to load system settings:", err);

      setError(
        err.response?.data?.message ||
          "Unable to load system configuration."
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const configurationSections = settings
    ? [
        {
          title: "Application",
          icon: "Application",
          items: [
            ["Application Name", settings.applicationName],
            ["Backend Port", settings.backendPort],
            ["Environment", settings.environment],
            ["Timezone", settings.timezone],
          ],
        },
        {
          title: "Database",
          icon: "Database",
          items: [["Database", settings.database]],
        },
        {
          title: "Infrastructure",
          icon: "Redis",
          items: [
            ["Redis", settings.redis],
            ["Kafka", settings.kafka],
            [
              "Kafka Consumer Group",
              settings.kafkaConsumerGroup,
            ],
          ],
        },
        {
          title: "Payment Integration",
          icon: "Razorpay",
          items: [
            ["Razorpay Webhook", settings.razorpayWebhook],
          ],
        },
        {
          title: "AI & Intelligence",
          icon: "AI",
          items: [
            ["AI Provider", settings.aiProvider],
            ["Chat Model", settings.chatModel],
            ["Embedding Model", settings.embeddingModel],
            ["Vector Store", settings.vectorStore],
          ],
        },
      ]
    : [];

  return (
    <div className="settings-page">

      {/* =====================================================
          PAGE HEADER
      ===================================================== */}

      <div className="page-heading settings-page-heading">

        <div>
          <div className="eyebrow">
            System Configuration
          </div>

          <h1>Settings</h1>

          <p>
            System configuration and infrastructure information
            for ReviveAI.
          </p>
        </div>

        <button
          className="analysis-button"
          onClick={loadSettings}
          disabled={loading}
        >
          <RefreshCw
            size={16}
            className={loading ? "spin" : ""}
          />

          Refresh
        </button>

      </div>

      {/* =====================================================
          NOTICE
      ===================================================== */}

      <div className="settings-notice">

        <div className="settings-notice-icon">
          <ShieldCheck size={21} />
        </div>

        <div>
          <h3>Read-only system configuration</h3>

          <p>
            These values describe the currently running
            ReviveAI environment. Sensitive credentials and
            secrets are never exposed.
          </p>
        </div>

      </div>

      {/* =====================================================
          LOADING
      ===================================================== */}

      {loading && (
        <div className="settings-state">

          <RefreshCw
            size={24}
            className="spin"
          />

          <p>
            Loading system configuration...
          </p>

        </div>
      )}

      {/* =====================================================
          ERROR
      ===================================================== */}

      {!loading && error && (
        <div className="settings-state settings-error-state">

          <AlertTriangle size={24} />

          <p>{error}</p>

          <button
            className="secondary-button"
            onClick={loadSettings}
          >
            Try Again
          </button>

        </div>
      )}

      {/* =====================================================
          CONFIGURATION
      ===================================================== */}

      {!loading && !error && settings && (
        <>

          {/* SYSTEM STATUS */}

          <div className="settings-status-card">

            <div className="settings-status-icon">
              <CheckCircle2 size={22} />
            </div>

            <div>
              <span>System Status</span>

              <strong>
                Configuration Loaded
              </strong>
            </div>

          </div>

          {/* CONFIGURATION CARDS */}

          <div className="settings-grid">

            {configurationSections.map((section) => {

              const Icon =
                settingIcons[section.icon] || Server;

              return (
                <div
                  className="settings-card"
                  key={section.title}
                >

                  <div className="settings-card-header">

                    <div className="settings-card-icon">
                      <Icon size={19} />
                    </div>

                    <div>
                      <h2>{section.title}</h2>

                      <span>
                        System configuration
                      </span>
                    </div>

                  </div>

                  <div className="settings-list">

                    {section.items.map(
                      ([label, value]) => (
                        <div
                          className="settings-row"
                          key={label}
                        >

                          <span>
                            {label}
                          </span>

                          <strong>
                            {value === null ||
                            value === undefined ||
                            value === ""
                              ? "Not configured"
                              : String(value)}
                          </strong>

                        </div>
                      )
                    )}

                  </div>

                </div>
              );
            })}

          </div>

          {/* CREDENTIAL PROTECTION */}

          <div className="settings-security-card">

            <div className="settings-security-icon">
              <ShieldCheck size={20} />
            </div>

            <div>

              <h3>
                Credential Protection
              </h3>

              <p>
                Database passwords, Razorpay secrets,
                API keys, webhook secrets, and other
                sensitive credentials remain server-side
                and are not returned by the Settings API.
              </p>

            </div>

          </div>

        </>
      )}

    </div>
  );
}

export default Settings;