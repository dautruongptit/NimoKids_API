-- login_history and security_events are append-only: nobody can rewrite the security history, not even by mistake.
-- The only exception is the retention function, which sets the transaction-local flag nimokids.retention = 'on'.

CREATE FUNCTION auth_history_immutable() RETURNS trigger AS $$
BEGIN
    IF current_setting('nimokids.retention', true) = 'on' THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% on % is not allowed: the table is append-only', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_login_history_immutable
    BEFORE UPDATE OR DELETE ON login_history
    FOR EACH ROW EXECUTE FUNCTION auth_history_immutable();

CREATE TRIGGER trg_security_events_immutable
    BEFORE UPDATE OR DELETE ON security_events
    FOR EACH ROW EXECUTE FUNCTION auth_history_immutable();

-- Retention: deletes history older than the given ages and generalises old IP addresses
-- (IPv4 -> /24, IPv6 -> /48). Called by the application's cleanup job with the values of auth_settings.
CREATE FUNCTION auth_apply_retention(
    login_history_days integer,
    security_event_days integer,
    audit_event_days integer,
    ip_generalise_after_days integer)
RETURNS TABLE (deleted_login_history bigint, deleted_security_events bigint, generalised_rows bigint) AS $$
DECLARE
    d1 bigint;
    d2 bigint;
    g1 bigint;
    g2 bigint;
BEGIN
    PERFORM set_config('nimokids.retention', 'on', true);

    DELETE FROM login_history WHERE occurred_at < now() - make_interval(days => login_history_days);
    GET DIAGNOSTICS d1 = ROW_COUNT;

    DELETE FROM security_events
    WHERE (category = 'SECURITY' AND occurred_at < now() - make_interval(days => security_event_days))
       OR (category = 'AUDIT'    AND occurred_at < now() - make_interval(days => audit_event_days));
    GET DIAGNOSTICS d2 = ROW_COUNT;

    UPDATE login_history
    SET ip = network(set_masklen(ip, CASE WHEN family(ip) = 4 THEN 24 ELSE 48 END))::inet
    WHERE ip IS NOT NULL AND occurred_at < now() - make_interval(days => ip_generalise_after_days)
      AND masklen(ip) = CASE WHEN family(ip) = 4 THEN 32 ELSE 128 END;
    GET DIAGNOSTICS g1 = ROW_COUNT;

    UPDATE security_events
    SET ip = network(set_masklen(ip, CASE WHEN family(ip) = 4 THEN 24 ELSE 48 END))::inet
    WHERE ip IS NOT NULL AND occurred_at < now() - make_interval(days => ip_generalise_after_days)
      AND masklen(ip) = CASE WHEN family(ip) = 4 THEN 32 ELSE 128 END;
    GET DIAGNOSTICS g2 = ROW_COUNT;

    PERFORM set_config('nimokids.retention', 'off', true);
    RETURN QUERY SELECT d1, d2, g1 + g2;
END;
$$ LANGUAGE plpgsql;
