Hướng dẫn nhanh: Build và xuất file .ipa bằng GitHub Actions

1) Mô tả
   - File workflow: `.github/workflows/build-ipa.yml`
   - Chạy trên `macos-latest`, dùng `fastlane gym` để build và export

2) Secrets cần thêm vào repo (Settings → Secrets):
   - `P12_BASE64`: Nội dung file certificate `.p12` được base64 encode
   - `P12_PASSWORD`: Mật khẩu file `.p12`
   - `PROVISION_BASE64`: Nội dung provisioning profile `.mobileprovision` base64 encode

3) Cách chạy
   - Vào tab Actions → chọn workflow `Build and export iOS .ipa` → Run workflow
   - Hoặc dùng GitHub CLI:
     ```bash
     gh workflow run build-ipa.yml --repo <owner>/<repo> -f project_path="ios/YourApp.xcworkspace" -f project_type=workspace -f scheme="YourScheme" -f export_method=ad-hoc
     ```

4) Kết quả
   - Khi chạy xong, artifact tên `ios-ipa` chứa `.ipa` sẽ có trong trang run của workflow.

5) Lưu ý
   - Cần biết `scheme` và đường dẫn workspace/project trong repo.
   - Nếu bạn không muốn dùng `fastlane`, workflow có thể chỉnh thành `xcodebuild` + `-exportOptionsPlist`.

   6) Tự động hóa secrets (encode + upload)

   - Có script tiện ích: `scripts/encode_and_set_secrets.sh` — nó mã hoá file `.p12` và `.mobileprovision` thành base64 và in ra hoặc tự upload lên GitHub nếu bạn cung cấp `GH_TOKEN` và `owner/repo`.

   Ví dụ sử dụng cục bộ (không upload):
   ```bash
   ./scripts/encode_and_set_secrets.sh ./certs/mycert.p12 "p12Password" ./profiles/myprofile.mobileprovision
   ```

   Ví dụ upload tự động (cần `GH_TOKEN` env và `gh` CLI đã login):
   ```bash
   export GH_TOKEN="<personal-access-token-with-repo:secrets:write>"
   ./scripts/encode_and_set_secrets.sh ./certs/mycert.p12 "p12Password" ./profiles/myprofile.mobileprovision owner/repo
   ```

   Sau đó chạy workflow như mô tả ở trên.
