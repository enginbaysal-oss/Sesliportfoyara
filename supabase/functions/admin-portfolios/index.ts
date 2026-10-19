import "@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const token = authHeader.replace("Bearer ", "").trim();

    if (!token) {
      return json({ error: "Yetki token yok." }, 401);
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabase = createClient(supabaseUrl, serviceRoleKey);

    const { data: userData, error: userError } = await supabase.auth.getUser(token);
    if (userError || !userData.user?.email) {
      return json({ error: "Kullanıcı doğrulanamadı." }, 401);
    }

    const email = userData.user.email.toLowerCase();

    const { data: allowed, error: allowedError } = await supabase
      .from("authorized_users")
      .select("is_admin,is_office_admin,is_active")
      .eq("email", email)
      .maybeSingle();

    if (allowedError) {
      return json({ error: allowedError.message }, 500);
    }

    if (!allowed?.is_active || !(allowed?.is_admin || allowed?.is_office_admin)) {
      return json({ error: "Bu işlem için admin/ofis admin yetkisi gerekir." }, 403);
    }

    const body = await req.json();
    const action = body.action;

    if (action === "ping") {
      return json({ ok: true, email, message: "admin-portfolios hazir" });
    }

    return json({ error: "Bilinmeyen işlem: " + action }, 400);
  } catch (e) {
    return json({ error: String(e?.message ?? e) }, 500);
  }
});

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}